package com.aetherianartificer.townstead.pheno.power;

/**
 * One unit of behavior an entity can carry, independent of how it was acquired. The
 * genetics system grants components through expressed genes ({@code GeneInstance});
 * the professions system will grant them through learned skills. Appliers (abilities,
 * triggers, modifiers, prevents, resources, ...) resolve components through
 * {@link Powers}, so they are blind to the source.
 *
 * <p>This is the shared root of the behavior layer: it depends on nothing in the
 * genetics or professions packages. Presentation (the gene picker's {@code display()})
 * is genetics-specific and lives on {@code GeneInstance}, not here.</p>
 */
public interface PowerComponent {

    /** The owning type's key (e.g. {@code townstead_origins:ability}). */
    String typeKey();
}
