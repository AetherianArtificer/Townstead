package com.aetherianartificer.townstead.farming.pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class FarmPatternSchemaValidator {
    private FarmPatternSchemaValidator() {}

    public static List<String> validate(JsonObject json) {
        List<String> errors = new ArrayList<>();

        String id = GsonHelper.getAsString(json, "id", "").trim();
        if (id.isEmpty()) errors.add("id is required");

        int tier = GsonHelper.getAsInt(json, "tier", -1);
        if (tier < 1 || tier > 5) errors.add("tier must be in range 1..5");
        int level = GsonHelper.getAsInt(json, "level", tier);
        if (level < 1 || level > 5) errors.add("level must be in range 1..5");
        int requiredTier = GsonHelper.getAsInt(json, "requiredTier", level);
        if (requiredTier < 1 || requiredTier > 5) errors.add("requiredTier must be in range 1..5");
        String family = GsonHelper.getAsString(json, "family", "").trim();
        if (family.isEmpty()) errors.add("family is required");

        JsonObject sizeObj = GsonHelper.getAsJsonObject(json, "size", null);
        int sizeX = -1;
        int sizeZ = -1;
        if (sizeObj != null) {
            sizeX = GsonHelper.getAsInt(sizeObj, "x", -1);
            sizeZ = GsonHelper.getAsInt(sizeObj, "z", -1);
            if (sizeX <= 0 || sizeZ <= 0) {
                errors.add("size.x and size.z must be > 0");
            }
        }

        List<String> grid = readGrid(json, errors);
        if (grid.isEmpty()) {
            errors.add("grid is required and must contain at least one row");
        }

        int width = -1;
        int height = grid.size();
        boolean[][] plantable = null;
        if (!grid.isEmpty()) {
            width = grid.getFirst().length();
            if (width <= 0) {
                errors.add("grid rows must not be empty");
            } else {
                plantable = new boolean[height][width];
                int plantableCount = 0;
                for (int z = 0; z < height; z++) {
                    String row = grid.get(z);
                    if (row.length() != width) {
                        errors.add("grid rows must have consistent width");
                        break;
                    }
                    for (int x = 0; x < width; x++) {
                        char c = row.charAt(x);
                        if (c == 'P') {
                            plantable[z][x] = true;
                            plantableCount++;
                        } else if (c != 'W' && c != '#' && c != '.') {
                            errors.add("grid contains invalid symbol '" + c + "' at (" + x + "," + z + "), allowed: P W # .");
                        }
                    }
                }
                if (plantableCount == 0) {
                    errors.add("grid must contain at least one plantable cell (P)");
                } else if (!isConnected(plantable)) {
                    errors.add("plantable work area must be connected");
                }
            }
        }

        if (sizeObj != null && width > 0 && height > 0 && (sizeX != width || sizeZ != height)) {
            errors.add("size does not match grid dimensions");
        }

        List<Cell> waterCells = readCells(json, "water_cells", errors);
        List<Cell> pathCells = readCells(json, "path_cells", errors);

        int boundsX = sizeObj != null ? sizeX : width;
        int boundsZ = sizeObj != null ? sizeZ : height;
        if (boundsX > 0 && boundsZ > 0) {
            validateInBounds("water_cells", waterCells, boundsX, boundsZ, errors);
            validateInBounds("path_cells", pathCells, boundsX, boundsZ, errors);
        }

        Set<Long> waterSet = toSet(waterCells);
        Set<Long> pathSet = toSet(pathCells);
        for (long packed : waterSet) {
            if (pathSet.contains(packed)) {
                errors.add("water_cells and path_cells overlap at (" + unpackX(packed) + "," + unpackZ(packed) + ")");
            }
        }

        if (!json.has("crop_rules") || !json.get("crop_rules").isJsonObject()) {
            errors.add("crop_rules object is required");
        }
        if (!json.has("constraints") || !json.get("constraints").isJsonObject()) {
            errors.add("constraints object is required");
        }
        if (!json.has("weights") || !json.get("weights").isJsonObject()) {
            errors.add("weights object is required");
        }

        return errors;
    }

    private static List<String> readGrid(JsonObject json, List<String> errors) {
        List<String> rows = new ArrayList<>();
        JsonArray array = GsonHelper.getAsJsonArray(json, "grid", null);
        if (array == null) return rows;
        for (int i = 0; i < array.size(); i++) {
            JsonElement row = array.get(i);
            if (!row.isJsonPrimitive() || !row.getAsJsonPrimitive().isString()) {
                errors.add("grid row " + i + " must be a string");
                continue;
            }
            rows.add(row.getAsString());
        }
        return rows;
    }

    private static List<Cell> readCells(JsonObject json, String key, List<String> errors) {
        List<Cell> cells = new ArrayList<>();
        JsonArray array = GsonHelper.getAsJsonArray(json, key, new JsonArray());
        for (int i = 0; i < array.size(); i++) {
            JsonElement e = array.get(i);
            if (!e.isJsonArray()) {
                errors.add(key + "[" + i + "] must be [x,z]");
                continue;
            }
            JsonArray pair = e.getAsJsonArray();
            if (pair.size() != 2 || !pair.get(0).isJsonPrimitive() || !pair.get(1).isJsonPrimitive()) {
                errors.add(key + "[" + i + "] must be [x,z]");
                continue;
            }
            try {
                int x = pair.get(0).getAsInt();
                int z = pair.get(1).getAsInt();
                cells.add(new Cell(x, z));
            } catch (Exception ex) {
                errors.add(key + "[" + i + "] has invalid integer coordinates");
            }
        }
        return cells;
    }

    private static void validateInBounds(String key, List<Cell> cells, int sizeX, int sizeZ, List<String> errors) {
        for (Cell cell : cells) {
            if (cell.x < 0 || cell.z < 0 || cell.x >= sizeX || cell.z >= sizeZ) {
                errors.add(key + " contains out-of-bounds cell (" + cell.x + "," + cell.z + ")");
            }
        }
    }

    private static Set<Long> toSet(List<Cell> cells) {
        Set<Long> set = new HashSet<>();
        for (Cell cell : cells) {
            set.add(pack(cell.x, cell.z));
        }
        return set;
    }

    private static boolean isConnected(boolean[][] plantable) {
        int h = plantable.length;
        int w = h == 0 ? 0 : plantable[0].length;
        int total = 0;
        int sx = -1;
        int sz = -1;
        for (int z = 0; z < h; z++) {
            for (int x = 0; x < w; x++) {
                if (!plantable[z][x]) continue;
                total++;
                if (sx < 0) {
                    sx = x;
                    sz = z;
                }
            }
        }
        if (total == 0) return false;

        boolean[][] seen = new boolean[h][w];
        ArrayDeque<Cell> q = new ArrayDeque<>();
        q.add(new Cell(sx, sz));
        seen[sz][sx] = true;
        int visited = 0;
        while (!q.isEmpty()) {
            Cell cur = q.removeFirst();
            visited++;
            int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
            for (int[] d : dirs) {
                int nx = cur.x + d[0];
                int nz = cur.z + d[1];
                if (nx < 0 || nz < 0 || nx >= w || nz >= h) continue;
                if (!plantable[nz][nx] || seen[nz][nx]) continue;
                seen[nz][nx] = true;
                q.addLast(new Cell(nx, nz));
            }
        }
        return visited == total;
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    private static int unpackX(long p) {
        return (int) (p >> 32);
    }

    private static int unpackZ(long p) {
        return (int) p;
    }

    private record Cell(int x, int z) {}
}
