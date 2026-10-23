// Trips with the player to a place on the map: the grave, the shepherd's grave and the court.
// The player gets the place marked on their map and leads; the founder follows.

// Start a trip.
=== function start_trip(ref on_trip, mark, travel) ===
~ act(mark)
~ act(travel)
~ on_trip = true

// The trip is over: the founder stops following and goes home.
=== function end_trip(ref on_trip, home) ===
~ on_trip = false
~ act(home)
