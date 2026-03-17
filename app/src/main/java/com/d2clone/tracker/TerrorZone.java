package com.d2clone.tracker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class TerrorZone {
    public final String name;
    public final String act;
    public final long timestamp;
    public final boolean isCurrent;

    public TerrorZone(String name, String act, long timestamp, boolean isCurrent) {
        this.name = name;
        this.act = act;
        this.timestamp = timestamp;
        this.isCurrent = isCurrent;
    }

    public static class Group {
        public final String name;
        public final String[] zones;

        public Group(String name, String... zones) {
            this.name = name;
            this.zones = zones;
        }

        public boolean contains(String zoneName) {
            if (zoneName == null || zoneName.isEmpty()) return false;
            for (String z : zones) {
                if (isFuzzyMatch(zoneName, z)) return true;
            }
            return false;
        }
    }

    /**
     * Specialized matching logic for the Exocet (Diablo) font.
     */
    public static boolean isFuzzyMatch(String input, String target) {
        if (input == null || target == null) return false;
        
        String nInput = input.toLowerCase().replaceAll("[^a-z0-9]", "");
        String nTarget = target.toLowerCase().replaceAll("[^a-z0-9]", "");
        
        if (nInput.isEmpty() || nTarget.isEmpty()) return false;

        // Common Exocet OCR misreadings fix
        nInput = nInput.replace("1", "m") // M often read as 1 (e.g. "1arsh")
                       .replace("0", "o") // 0 as O
                       .replace("5", "s"); // 5 as S

        if (nInput.equals(nTarget)) return true;

        // Exocet Font Fix: remove 'o' from both and compare.
        // Stylized 'O' is often dropped ("Outer" -> "uter") or misread ("of" -> "f").
        String noOInput = nInput.replace("o", "");
        String noOTarget = nTarget.replace("o", "");
        
        if (!noOInput.isEmpty() && noOInput.equals(noOTarget)) return true;

        if (noOInput.length() >= 4 && noOTarget.length() >= 4) {
            if (noOInput.contains(noOTarget) || noOTarget.contains(noOInput)) return true;
        }

        return false;
    }

    /**
     * Consolidated logic to find all zones mentioned in a line of OCR text.
     */
    public static List<String> findZonesInText(String text) {
        String haystack = text.toLowerCase();
        String cleanHaystack = haystack.replaceAll("[^a-z0-9 ]", " ").replaceAll(" +", " ").trim();
        
        List<String> results = new ArrayList<>();
        if (cleanHaystack.isEmpty()) return results;

        for (String zone : ALL_ZONES) {
            String needle = zone.toLowerCase().replaceAll("[^a-z0-9]", "");
            if (needle.length() < 3) continue;
            
            if (needle.equals("hole") || needle.equals("pit") || needle.equals("cave")) {
                if (Pattern.compile("\\b" + needle + "\\b").matcher(cleanHaystack).find()) {
                    results.add(zone);
                }
            } else {
                if (isFuzzyMatch(cleanHaystack, zone)) {
                    results.add(zone);
                }
            }
        }
        
        // Remove sub-matches
        List<String> filtered = new ArrayList<>();
        for (String r : results) {
            boolean isSub = false;
            for (String other : results) {
                if (!r.equals(other) && other.toLowerCase().contains(r.toLowerCase())) {
                    isSub = true; break;
                }
            }
            if (!isSub) filtered.add(r);
        }
        return filtered;
    }

    /**
     * Consolidated logic to find the best matching Group for a set of detected zones.
     */
    public static Group findBestGroup(Set<String> detectedZones) {
        if (detectedZones == null || detectedZones.isEmpty()) return null;
        
        Map<Group, Integer> scores = new HashMap<>();
        for (String z : detectedZones) {
            for (Group g : GROUPS) {
                if (g.contains(z)) {
                    int score = scores.getOrDefault(g, 0);
                    scores.put(g, score + (z.length() * z.length()));
                }
            }
        }
        
        Group best = null;
        int maxScore = -1;
        for (Map.Entry<Group, Integer> entry : scores.entrySet()) {
            if (entry.getValue() > maxScore) {
                maxScore = entry.getValue();
                best = entry.getKey();
            }
        }
        return best;
    }

    public static Group getGroupForZone(String zoneName) {
        for (Group g : GROUPS) {
            if (g.contains(zoneName)) return g;
        }
        return null;
    }

    public static final Group[] GROUPS = {
        // Act 1
        new Group("Blood Moor, Den of Evil", "Blood Moor", "Den of Evil", "Blood Moor, Den of Evil"),
        new Group("Cold Plains, The Cave", "Cold Plains", "Cave", "The Cave", "Cold Plains, The Cave"),
        new Group("Burial Grounds, Crypt, Mausoleum", "Burial Grounds", "Crypt", "Mausoleum"),
        new Group("Stony Field, Tristram", "Stony Field", "Tristram", "Stony Field, Tristram"),
        new Group("Dark Wood, Underground Passage", "Dark Wood", "Underground Passage", "Underground Passage Level 1", "Underground Passage Level 2"),
        new Group("Black Marsh, The Hole, Forgotten Tower", "Black Marsh", "Hole", "The Hole", "Forgotten Tower", "The Forgotten Tower", "Black Marsh, The Hole, Forgotten Tower"),
        new Group("Jail, Barracks", "Jail", "Jail Level 1", "Jail Level 2", "Jail Level 3", "Barracks", "Jail, Barracks"),
        new Group("Cathedral, Inner Cloister, Catacombs", "Cathedral", "Inner Cloister", "Catacombs", "Catacombs Level 1", "Catacombs Level 2", "Catacombs Level 3", "Catacombs Level 4"),
        new Group("Tamoe Highland, Pit, Monastery Gate, Outer Cloister", "Tamoe Highland", "Pit", "The Pit", "Monastery Gate", "Outer Cloister", "Tamoe Highland, Pit, Monastery Gate, Outer Cloister"),
        new Group("Moo Moo Farm", "Moo Moo Farm", "The Secret Cow Level"),

        // Act 2
        new Group("Lut Gholein Sewers", "Lut Gholein Sewers", "Sewers Level 1", "Sewers Level 2", "Sewers Level 3"),
        new Group("Rocky Waste, Stony Tomb", "Rocky Waste", "Stony Tomb", "Rocky Waste, Stony Tomb"),
        new Group("Dry Hills, Halls of the Dead", "Dry Hills", "Halls of the Dead", "Dry Hills, Halls of the Dead"),
        new Group("Far Oasis, Maggot Lair", "Far Oasis", "Maggot Lair", "Far Oasis, Maggot Lair"),
        new Group("Lost City, Valley of Snakes, Claw Viper Temple, Ancient Tunnels", "Lost City", "Valley of Snakes", "Claw Viper Temple", "Ancient Tunnels"),
        new Group("Arcane Sanctuary, Harem, Palace Cellar", "Arcane Sanctuary", "Harem", "Palace Cellar"),
        new Group("Tal Rasha's Tomb, Canyon of the Magi", "Tal Rasha's Tomb", "Tal Rasha's Tombs", "Tal Rasha's Chamber", "Canyon of the Magi", "Tal Rasha's Tomb, Canyon of the Magi"),

        // Act 3
        new Group("Spider Forest, Arachnid Lair, Spider Cavern", "Spider Forest", "Arachnid Lair", "Spider Cavern"),
        new Group("Great Marsh", "Great Marsh"),
        new Group("Flayer Jungle, Flayer Dungeon, Swampy Pit", "Flayer Jungle", "Flayer Dungeon", "Swampy Pit"),
        new Group("Kurast Bazaar, Kurast Causeway, Kurast Sewers, Temples", "Kurast Bazaar", "Kurast Causeway", "Kurast Sewers", "Ruined Temple", "Disused Fane", "Forgotten Reliquary", "Forgotten Temple", "Ruined Fane", "Disused Reliquary", "Upper Kurast"),
        new Group("Travincal", "Travincal"),
        new Group("Durance of Hate", "Durance of Hate"),

        // Act 4
        new Group("Outer Steppes, Plains of Despair", "Outer Steppes", "Plains of Despair", "Outer Steppes, Plains of Despair"),
        new Group("River of Flame, City of the Damned", "River of Flame", "City of the Damned", "River of Flame, City of the Damned"),
        new Group("Chaos Sanctuary", "Chaos Sanctuary"),

        // Act 5
        new Group("Bloody Foothills, Frigid Highlands, Abaddon", "Bloody Foothills", "Frigid Highlands", "Abaddon", "Bloody Foothills, Frigid Highlands, Abaddon"),
        new Group("Arreat Plateau, Pit of Acheron", "Arreat Plateau", "Pit of Acheron", "Arreat Plateau, Pit of Acheron"),
        new Group("Crystalline Passage, Frozen River", "Crystalline Passage", "Frozen River", "Crystalline Passage, Frozen River"),
        new Group("Glacial Trail, Drifter Cavern", "Glacial Trail", "Drifter Cavern", "Glacial Trail, Drifter Cavern"),
        new Group("Frozen Tundra, Infernal Pit", "Frozen Tundra", "Infernal Pit", "Frozen Tundra, Infernal Pit"),
        new Group("The Ancients' Way, Icy Cellar", "The Ancients' Way", "Icy Cellar", "Ancients Way", "The Ancients' Way, Icy Cellar"),
        new Group("Nihlathak's Temple, Temple Halls", "Nihlathak's Temple", "Temple Halls", "Halls of Anguish", "Halls of Pain", "Halls of Vaught", "Nihlathak's Temple, Temple Halls"),
        new Group("Worldstone Keep, Throne of Destruction, Worldstone Chamber", "Worldstone Keep", "Throne of Destruction", "Worldstone Chamber", "Worldstone Keep, Throne of Destruction, Worldstone Chamber")
    };

    // Keep ALL_ZONES for fuzzy matching/OCR
    public static final String[] ALL_ZONES = {
        "Blood Moor", "Den of Evil", "Blood Moor, Den of Evil",
        "Cold Plains", "Cave", "The Cave", "Cold Plains, The Cave",
        "Burial Grounds", "Crypt", "Mausoleum", "Stony Field", "Tristram", "Stony Field, Tristram",
        "Underground Passage", "Underground Passage Level 1", "Underground Passage Level 2", "Dark Wood", 
        "Black Marsh", "Hole", "The Hole", "Forgotten Tower", "The Forgotten Tower", "Black Marsh, The Hole, Forgotten Tower",
        "Jail", "Jail Level 1", "Jail Level 2", "Jail Level 3", "Barracks", "Jail, Barracks",
        "Inner Cloister", "Outer Cloister", "Monastery Gate", "Cathedral", 
        "Catacombs", "Catacombs Level 1", "Catacombs Level 2", "Catacombs Level 3", "Catacombs Level 4", 
        "Tamoe Highland", "Pit", "The Pit", "Tamoe Highland, Pit, Monastery Gate, Outer Cloister",
        "Moo Moo Farm", "The Secret Cow Level",
        "Rocky Waste", "Stony Tomb", "Rocky Waste, Stony Tomb",
        "Dry Hills", "Halls of the Dead", "Dry Hills, Halls of the Dead",
        "Far Oasis", "The Far Oasis", "Maggot Lair", "The Maggot Lair", "Far Oasis, Maggot Lair",
        "Lost City", "Valley of Snakes", "Claw Viper Temple", "Ancient Tunnels", "Harem", "Palace Cellar", "Arcane Sanctuary",
        "Canyon of the Magi", "Tal Rasha's Tombs", "Tal Rasha's Tomb", "Tal Rasha's Chamber", "Tal Rasha's Tomb, Canyon of the Magi",
        "Lut Gholein Sewers", "Spider Forest", "Spider Cavern", "Arachnid Lair", "Great Marsh", "Flayer Jungle", 
        "Flayer Dungeon", "Swampy Pit", "Ruined Temple", "Disused Fane", "Forgotten Reliquary", "Kurast Bazaar", "Upper Kurast",
        "Forgotten Temple", "Kurast Causeway", "Travincal", "Durance of Hate", "Kurast Sewers", "Ruined Fane", "Disused Reliquary",
        "Outer Steppes", "Plains of Despair", "Outer Steppes, Plains of Despair",
        "City of the Damned", "River of Flame", "River of Flame, City of the Damned",
        "Chaos Sanctuary",
        "Bloody Foothills", "Frigid Highlands", "Abaddon", "Bloody Foothills, Frigid Highlands, Abaddon",
        "Arreat Plateau", "Pit of Acheron", "Arreat Plateau, Pit of Acheron",
        "Crystalline Passage", "Frozen River", "Crystalline Passage, Frozen River",
        "Glacial Trail", "Drifter Cavern", "Glacial Trail, Drifter Cavern",
        "Frozen Tundra", "Infernal Pit", "Frozen Tundra, Infernal Pit",
        "The Ancients' Way", "Icy Cellar", "Ancients Way", "The Ancients' Way, Icy Cellar",
        "Nihlathak's Temple", "Halls of Anguish", "Halls of Pain", "Halls of Vaught", "Temple Halls", "Nihlathak's Temple, Temple Halls",
        "Worldstone Keep", "Throne of Destruction", "Worldstone Chamber", "Worldstone Keep, Throne of Destruction, Worldstone Chamber"
    };
}
