/*
 * Copyright (c) 2026, Vani-Lab
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.chunkblazer;

import com.chunkblazer.TaskTargetHighlighter.Rule;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.Skill;

/**
 * Extra knowledge of where tasks happen, for the UI only: outlines, the right-click
 * Tasks menu and auto-tracking. The task data and completion tracking never read
 * this, so nothing here changes how a task is completed.
 *
 * Three kinds of extra:
 *  - Name rules, for tasks whose target can be told from the task's own wording:
 *    farming (crop to patch), hunter creatures, stalls, pickpocketing, STASH units,
 *    Imp Catcher beads (imps), and the few kill tasks with no NPC ids.
 *  - Ids, for tasks whose target has a generic name (agility shortcuts and courses,
 *    cocktails, drops from monsters in the same chunk).
 *  - Inventory tools, for tasks done from the inventory: right-clicking a knife lists
 *    fletching tasks, a tinderbox firemaking, a pestle and mortar herblore, and so on
 *    (see matchesItem).
 *
 * Only used for tasks that have no NPC or object ids of their own.
 */
public final class TaskTargetExtras
{
	private TaskTargetExtras()
	{
	}

	private static final Map<String, Set<Integer>> EXTRA_OBJECTS = new HashMap<>();
	private static final Map<String, Set<Integer>> EXTRA_NPCS = new HashMap<>();

	/** Task wording (letters only, lowercase) to the in-game name, where they differ. */
	private static final Map<String, String> NAME_ALIASES = new HashMap<>();

	/**
	 * Tasks linked to an NPC by its name (for NPCs whose id varies or isn't known):
	 * task id to the NPC's name in game.
	 */
	private static final Map<String, String> NAMED_NPCS = new HashMap<>();

	/**
	 * Real level requirements for tasks whose own data doesn't hold them all: equip tasks
	 * (from each item's equipment requirements, every skill it needs) and a couple of
	 * prayers. Used by the UI's "can you do it" checks; the plugin's own check still applies.
	 */
	private static final Map<String, Map<Skill, Integer>> REQUIREMENTS = new HashMap<>();

	/** Crop (part of the task name or seed) to the patch it grows in. Checked in order. */
	private static final Map<String, String> PATCHES = new LinkedHashMap<>();

	static
	{
		NAME_ALIASES.put("ardougneknight", "knightofardougne");
		NAME_ALIASES.put("pollnivneachbandit", "bandit");
		NAME_ALIASES.put("redchinchompa", "carnivorouschinchompa");
		NAME_ALIASES.put("sabretoothkebbit", "sabretoothedkebbit");
		NAME_ALIASES.put("sabretoothkyatt", "sabretoothedkyatt");

		// Special patches first, so "palm tree" isn't read as a plain tree.
		for (String crop : new String[]{"teak", "mahogany"})
		{
			PATCHES.put(crop, "hardwood");
		}
		PATCHES.put("spirit", "spirit tree patch");
		PATCHES.put("calquat", "calquat patch");
		PATCHES.put("cactus", "cactus patch");
		PATCHES.put("belladonna", "belladonna patch");
		PATCHES.put("seaweed", "seaweed patch");
		for (String crop : new String[]{"apple", "banana", "orange", "curry", "pineapple", "papaya", "palm", "dragonfruit"})
		{
			PATCHES.put(crop, "fruit tree patch");
		}
		for (String crop : new String[]{"oak", "willow", "maple", "yew", "magic"})
		{
			PATCHES.put(crop, "tree patch");
		}
		for (String crop : new String[]{"redberr", "cadava", "dwellberr", "jangerberr", "whiteberr", "poison ivy"})
		{
			PATCHES.put(crop, "bush patch");
		}
		for (String crop : new String[]{"barley", "hammerstone", "asgarnian", "jute", "yanillian", "krandorian",
			"wildblood", "flax"})
		{
			PATCHES.put(crop, "hops patch");
		}
		for (String crop : new String[]{"marigold", "rosemary", "nasturtium", "woad", "limpwurt", "white lily"})
		{
			PATCHES.put(crop, "flower patch");
		}
		for (String crop : new String[]{"guam", "marrentill", "tarromin", "harralander", "ranarr", "toadflax",
			"irit", "avantoe", "kwuarm", "snapdragon", "cadantine", "lantadyme", "dwarf weed", "torstol"})
		{
			PATCHES.put(crop, "herb patch");
		}
		for (String crop : new String[]{"potato", "onion", "cabbage", "tomato", "sweetcorn", "strawberr",
			"watermelon", "snape grass"})
		{
			PATCHES.put(crop, "allotment");
		}

		// Barrows: the boss-chunk tasks with no target of their own go on the Strange Old Man,
		// who wanders the mounds (the brothers' own kill tasks keep their NPC ids).
		NAMED_NPCS.put("barrows_loot_chest", "Strange Old Man");
		NAMED_NPCS.put("barrows_1v1", "Strange Old Man");
		NAMED_NPCS.put("barrows_lifestealers", "Strange Old Man");
		NAMED_NPCS.put("barrows_a_rock_hard_place", "Strange Old Man");
		NAMED_NPCS.put("barrows_high_noon", "Strange Old Man");
		NAMED_NPCS.put("barrows_when_in_rome", "Strange Old Man");
		NAMED_NPCS.put("barrows_bare_chest", "Strange Old Man");
		NAMED_NPCS.put("barrows_ca_easy", "Strange Old Man");
		NAMED_NPCS.put("barrows_ca_medium", "Strange Old Man");
		NAMED_NPCS.put("barrows_ca_hard", "Strange Old Man");
		NAMED_NPCS.put("barrows_equip_veracs_set", "Strange Old Man");
		NAMED_NPCS.put("barrows_equip_ahrims_set", "Strange Old Man");
		NAMED_NPCS.put("barrows_equip_torags_set", "Strange Old Man");
		NAMED_NPCS.put("barrows_equip_dharoks_set", "Strange Old Man");
		NAMED_NPCS.put("barrows_equip_guthans_set", "Strange Old Man");
		NAMED_NPCS.put("barrows_equip_karils_set", "Strange Old Man");
		NAMED_NPCS.put("barrows_equip_any_piece", "Strange Old Man");

		// --- Gnome cocktails: made at, and taught by, Blurberry in the Grand Tree ---
		EXTRA_NPCS.put("cook_blurberry_special", ids(6531)); // Blurberry
		EXTRA_NPCS.put("cook_fruit_blast", ids(6531)); // Blurberry
		EXTRA_NPCS.put("cook_pineapple_punch", ids(6531)); // Blurberry
		EXTRA_NPCS.put("cook_wizard_blizzard", ids(6531)); // Blurberry (Make a Wizard Blizzard)
		EXTRA_NPCS.put("cook_short_green_guy", ids(6531)); // Blurberry (Make a Short Green Guy)
		EXTRA_NPCS.put("cook_drunk_dragon", ids(6531)); // Blurberry (Make a Drunk Dragon)
		EXTRA_NPCS.put("cook_chocolate_saturday", ids(6531)); // Blurberry (Make a Chocolate Saturday)

		// --- Obtain/equip tasks: monsters in the same chunk that drop the item ---
		// (matched from the monsters' drop tables; generated, so check any that look odd)
		EXTRA_NPCS.put("obtain_goblin_mail", ids(3028, 3029, 3030, 3031, 3032, 3033, 3034, 3035, 3036, 3037, 3038, 3039, 3040, 3041, 3042, 3043, 3044, 3051, 3052, 3053, 3054)); // Obtain Goblin Mail (?): Goblin
		EXTRA_NPCS.put("obtain_grimy_guam", ids(648, 649, 650, 651, 652, 653, 654, 699, 700, 701, 702, 703, 704, 705, 931, 932, 933, 934, 935, 936, 937, 938, 939, 940, 941, 942, 1877, 3014, 3015, 3106, 3107, 3108, 3109, 3110, 3111, 3112, 3113)); // Obtain a Grimy Guam Leaf (Al Kharid Toll Gate): Man, Woman
		EXTRA_NPCS.put("equip_rune_square_shield", ids(717, 720, 721, 722, 723, 725, 726, 727, 728)); // Equip a Rune Square Shield (Ancient Pyramid): Mummy
		EXTRA_NPCS.put("equip_ancient_staff", ids(717, 720, 721, 722, 723, 725, 726, 727, 728)); // Equip an Ancient Staff (Ancient Pyramid): Mummy
		EXTRA_NPCS.put("obtain_mossy_key", ids(2090, 2091, 2092, 2093)); // Obtain a Mossy Key (Black Chinchompas): Moss giant
		EXTRA_NPCS.put("obtain_ensouled_scorpion_head", ids(2479, 2480, 3024, 3025, 3026, 3027)); // Obtain an Ensouled Scorpion Head (Black Knights' Fortress Overlook): King Scorpion, Pit Scorpion, Poison Scorpion, Scorpion
		EXTRA_NPCS.put("obtain_trading_sticks", ids(530)); // Obtain Trading Sticks (Brimhaven Dungeon): Tribesman
		EXTRA_NPCS.put("obtain_grimy_irit", ids(520)); // Obtain a Grimy Irit Leaf (Chaos Druid Tower): Chaos druid
		EXTRA_NPCS.put("obtain_grimy_ranarr", ids(520, 648, 649, 650, 651, 652, 653, 654, 699, 700, 701, 702, 703, 704, 705, 931, 932, 933, 934, 935, 936, 937, 938, 939, 940, 941, 942, 1877)); // Obtain a Grimy Ranarr Weed (Chaos Druid Tower): Chaos druid
		EXTRA_NPCS.put("equip_spirit_shield", ids(319)); // Equip a Spirit Shield (Corporeal Beast): Corporeal Beast
		EXTRA_NPCS.put("obtain_holy_elixir", ids(319)); // Obtain a Holy Elixir (Corporeal Beast): Corporeal Beast
		EXTRA_NPCS.put("obtain_jar_of_spirits", ids(319)); // Obtain a Jar of Spirits (Corporeal Beast): Corporeal Beast
		EXTRA_NPCS.put("obtain_spectral_sigil", ids(319)); // Obtain a Spectral Sigil (Corporeal Beast): Corporeal Beast
		EXTRA_NPCS.put("obtain_arcane_sigil", ids(319)); // Obtain an Arcane Sigil (Corporeal Beast): Corporeal Beast
		EXTRA_NPCS.put("obtain_elysian_sigil", ids(319)); // Obtain an Elysian Sigil (Corporeal Beast): Corporeal Beast
		EXTRA_NPCS.put("dagannoth_kings_equip_berserker_ring", ids(2267)); // Equip a Berserker Ring (Dagannoth Kings): Dagannoth Rex
		EXTRA_NPCS.put("dagannoth_kings_equip_mud_battlestaff", ids(2266)); // Equip a Mud Battlestaff (Dagannoth Kings): Dagannoth Prime
		EXTRA_NPCS.put("dagannoth_kings_equip_seercull", ids(2265)); // Equip a Seercull (Dagannoth Kings): Dagannoth Supreme
		EXTRA_NPCS.put("dagannoth_kings_equip_seers_ring", ids(2266)); // Equip a Seers Ring (Dagannoth Kings): Dagannoth Prime
		EXTRA_NPCS.put("dagannoth_kings_equip_warrior_ring", ids(2267)); // Equip a Warrior Ring (Dagannoth Kings): Dagannoth Rex
		EXTRA_NPCS.put("dagannoth_kings_equip_archers_ring", ids(2265)); // Equip an Archers Ring (Dagannoth Kings): Dagannoth Supreme
		EXTRA_NPCS.put("dagannoth_kings_obtain_pet_prime", ids(2266)); // Obtain the Dagannoth Prime Pet (Dagannoth Kings): Dagannoth Prime
		EXTRA_NPCS.put("dagannoth_kings_obtain_pet_rex", ids(2267)); // Obtain the Dagannoth Rex Pet (Dagannoth Kings): Dagannoth Rex
		EXTRA_NPCS.put("dagannoth_kings_obtain_pet_supreme", ids(2265)); // Obtain the Dagannoth Supreme Pet (Dagannoth Kings): Dagannoth Supreme
		EXTRA_NPCS.put("Obtain_water_talisman", ids(510, 512, 2056, 2057, 2058, 2059)); // Obtain a Water Talisman (Dark Wizard's Tower): Dark wizard
		EXTRA_NPCS.put("obtain_black_robe", ids(510, 512, 2056, 2057, 2058, 2059)); // Obtain a Black Robe (Dark Wizards' Stone Table): Dark wizard
		EXTRA_NPCS.put("obtain_fire_talisman", ids(510, 512, 2056, 2057, 2058, 2059)); // Obtain a Fire Talisman (Dark Wizards' Stone Table): Dark wizard
		EXTRA_NPCS.put("equip_adamant_warhammer", ids(931, 932, 933, 934, 935, 936, 937, 938, 939, 940, 941, 942)); // Equip an Adamant Warhammer (Death Plateau): Mountain troll, Thrower Troll
		EXTRA_NPCS.put("obtain_grimy_marrentill", ids(648, 649, 650, 651, 652, 653, 654, 699, 700, 701, 702, 703, 704, 705, 931, 932, 933, 934, 935, 936, 937, 938, 939, 940, 941, 942, 1877)); // Obtain a Grimy Marrentill (Death Plateau): Ice Troll, Ice troll, Ice troll grunt, Mountain troll, Thrower Troll
		EXTRA_NPCS.put("obtain_grimy_tarromin", ids(648, 649, 650, 651, 652, 653, 654, 699, 700, 701, 702, 703, 704, 705, 931, 932, 933, 934, 935, 936, 937, 938, 939, 940, 941, 942, 1877)); // Obtain a Grimy Tarromin (Death Plateau): Ice Troll, Ice troll, Ice troll grunt, Mountain troll, Thrower Troll
		EXTRA_NPCS.put("obtain_black_cape", ids(518)); // Obtain a Black Cape (Draynor North): Highwayman
		EXTRA_NPCS.put("obtain_grimy_avantoe", ids(304, 520)); // Obtain a Grimy Avantoe (East Yanille): Salarin the twisted
		EXTRA_NPCS.put("obtain_grimy_kwuarm", ids(304, 520)); // Obtain a Grimy Kwuarm (East Yanille): Salarin the twisted
		EXTRA_NPCS.put("obtain_sinister_key", ids(304)); // Obtain a Sinister Key (East Yanille): Salarin the twisted
		EXTRA_NPCS.put("obtain_ensouled_ogre_head", ids(136, 1153, 2095, 2096, 2233)); // Obtain an Ensouled Ogre Head (Feldip Hills Fairy ring): Ogre
		EXTRA_NPCS.put("equip_leaf-bladed_battleaxe", ids(410, 411)); // Equip a Leaf-bladed Battleaxe (Fremennik Slayer Dungeon): Kurask
		EXTRA_NPCS.put("equip_leaf-bladed_sword", ids(410, 411, 427, 428, 429, 430)); // Equip a Leaf-bladed Sword (Fremennik Slayer Dungeon): Kurask, Turoth
		EXTRA_NPCS.put("gwd_graardor_equip_boots", ids(2215)); // Equip Bandos Boots (Godwars): General Graardor
		EXTRA_NPCS.put("gwd_graardor_equip_tassets", ids(2215)); // Equip Bandos Tassets (Godwars): General Graardor
		EXTRA_NPCS.put("gwd_graardor_equip_chestplate", ids(2215)); // Equip a Bandos Chestplate (Godwars): General Graardor
		EXTRA_NPCS.put("gwd_zilyana_equip_sara_sword", ids(2205)); // Equip a Saradomin Sword (Godwars): Commander Zilyana
		EXTRA_NPCS.put("gwd_kril_equip_staff_of_the_dead", ids(3129)); // Equip a Staff of the Dead (Godwars): K'ril Tsutsaroth
		EXTRA_NPCS.put("gwd_kril_equip_steam_battlestaff", ids(3129)); // Equip a Steam Battlestaff (Godwars): K'ril Tsutsaroth
		EXTRA_NPCS.put("gwd_kril_equip_zammy_spear", ids(3129)); // Equip a Zamorakian Spear (Godwars): K'ril Tsutsaroth
		EXTRA_NPCS.put("gwd_kreearra_equip_chainskirt", ids(3162)); // Equip an Armadyl Chainskirt (Godwars): Kree'arra
		EXTRA_NPCS.put("gwd_kreearra_equip_chestplate", ids(3162)); // Equip an Armadyl Chestplate (Godwars): Kree'arra
		EXTRA_NPCS.put("gwd_zilyana_equip_acb", ids(2205)); // Equip an Armadyl Crossbow (Godwars): Commander Zilyana
		EXTRA_NPCS.put("gwd_kreearra_equip_helm", ids(3162)); // Equip an Armadyl Helmet (Godwars): Kree'arra
		EXTRA_NPCS.put("gwd_zilyana_obtain_pet", ids(2205)); // Obtain the Commander Zilyana Pet (Godwars): Commander Zilyana
		EXTRA_NPCS.put("gwd_graardor_obtain_pet", ids(2215)); // Obtain the General Graardor Pet (Godwars): General Graardor
		EXTRA_NPCS.put("gwd_kril_obtain_pet", ids(3129)); // Obtain the K'ril Tsutsaroth Pet (Godwars): K'ril Tsutsaroth
		EXTRA_NPCS.put("gwd_kreearra_obtain_pet", ids(3162)); // Obtain the Kree'arra Pet (Godwars): Kree'arra
		EXTRA_NPCS.put("equip_rune_rune_full_helm", ids(2025, 2026, 2027, 2028, 2029, 2030, 2031, 2032)); // Equip a Rune Full Helm (Gu'Tanoth): Greater demon
		EXTRA_NPCS.put("equip_steel_dagger", ids(2536, 2537, 2538)); // Equip a Steel Dagger (H.A.M. Hideout): H.A.M. Guard
		EXTRA_NPCS.put("steal_rusty_sword", ids(2536, 2537, 2538)); // Obtain a Rusty Sword (H.A.M. Hideout): H.A.M. Guard
		EXTRA_NPCS.put("obtain_ensouled_goblin_head", ids(3028, 3029, 3030, 3031, 3032, 3033, 3034, 3035, 3036, 3037, 3038, 3039, 3040, 3041, 3042, 3043, 3044, 3051, 3052, 3053, 3054)); // Obtain an Ensouled Goblin Head (H.A.M. Hideout): Goblin
		EXTRA_NPCS.put("obtain_uncut_jade", ids(2536, 2537, 2538)); // Obtain an Uncut Jade (H.A.M. Hideout): H.A.M. Guard
		EXTRA_NPCS.put("obtain_uncut_opal", ids(2536, 2537, 2538)); // Obtain an Uncut Opal (H.A.M. Hideout): H.A.M. Guard
		EXTRA_NPCS.put("equip_rune_battleaxe", ids(648, 649, 650, 651, 652, 653, 654, 699, 700, 701, 702, 703, 704, 705)); // Equip a Rune Battleaxe (Ice Gate): Ice Troll, Ice troll
		EXTRA_NPCS.put("equip_adamant_kiteshield", ids(648, 649, 650, 651, 652, 653, 654, 699, 700, 701, 702, 703, 704, 705)); // Equip an Adamant Kiteshield (Ice Gate): Ice Troll, Ice troll
		EXTRA_NPCS.put("obtain_ensouled_troll_head", ids(648, 649, 650, 651, 652, 653, 654, 699, 700, 701, 702, 703, 704, 705, 931, 932, 933, 934, 935, 936, 937, 938, 939, 940, 941, 942, 1877)); // Obtain an Ensouled Troll Head (Ice Gate): Ice Troll, Ice troll
		EXTRA_NPCS.put("equip_light_mystic_hat", ids(417)); // Equip a Light Mystic Hat (Island of Stone): Basilisk
		EXTRA_NPCS.put("obtain_basilisk_head", ids(417)); // Obtain a Basilisk Head (Island of Stone): Basilisk
		EXTRA_NPCS.put("obtain_ogre_coffin_key", ids(866, 867, 868, 869, 870, 871, 872, 873, 874, 875, 876, 877, 878, 879)); // Obtain a Ogre Coffin Key (Jiggig): Skogre, Zogre
		EXTRA_NPCS.put("obtain_ourg_bone", ids(882)); // Obtain an Ourg Bone (Jiggig): Slash Bash
		EXTRA_NPCS.put("kalphite_queen_obtain_head", ids(965)); // Obtain the Kalphite Queen Head (Kalphite Queen): Kalphite Queen
		EXTRA_NPCS.put("kalphite_queen_obtain_pet", ids(965)); // Obtain the Kalphite Queen Pet (Kalphite Queen): Kalphite Queen
		EXTRA_NPCS.put("jad_obtain_pet", ids(3127)); // Obtain the Jad Pet (Karamja Volcano): TzTok-Jad
		EXTRA_NPCS.put("equip_dragon_med_helm", ids(239)); // Equip a Dragon Med Helm (King Black Dragon): King Black Dragon
		EXTRA_NPCS.put("equip_rune_longsword", ids(239)); // Equip a Rune Longsword (King Black Dragon): King Black Dragon
		EXTRA_NPCS.put("obtain_kbd_heads", ids(239)); // Obtain KBD Heads (King Black Dragon): King Black Dragon
		EXTRA_NPCS.put("obtain_ancient_shard", ids(240, 1432, 2048, 2049, 2050, 2051, 2052)); // Obtain an Ancient Shard (Kourend Castle): Black demon
		EXTRA_NPCS.put("equip_black_sq_shield", ids(2090, 2091, 2092, 2093)); // Equip a Black Square Shield (Land's End Moss Giants): Moss giant
		EXTRA_NPCS.put("equip_mithril_sword", ids(2090, 2091, 2092, 2093)); // Equip a Mithril Sword (Land's End Moss Giants): Moss giant
		EXTRA_NPCS.put("equip_rune_med_helm", ids(2005, 2006, 2007, 2008, 2018)); // Equip a Rune Med Helm (Lava Maze): Lesser demon
		EXTRA_NPCS.put("obtain_ensouled_demon_head", ids(2005, 2006, 2007, 2008, 2018)); // Obtain an Ensouled Demon Head (Lava Maze): Lesser demon
		EXTRA_NPCS.put("obtain_ensouled_dagannoth_head", ids(970, 971, 972, 973, 974, 975, 979, 2265, 2266, 2267)); // Obtain an Ensouled Dagannoth Head (Lighthouse): Dagannoth, Dagannoth Prime, Dagannoth Rex, Dagannoth Supreme
		EXTRA_NPCS.put("equip_bronze_med_helm", ids(3114)); // Equip a Bronze Med Helm (Lumbridge North Farm): Farmer
		EXTRA_NPCS.put("obtain_cowhide", ids(2790, 2791, 2793)); // Obtain Cowhide (Lumbridge North Farm): Cow
		EXTRA_NPCS.put("obtain_raw_beef", ids(2790, 2791, 2793)); // Obtain Raw Beef (Lumbridge North Farm): Cow
		EXTRA_NPCS.put("obtain_candle", ids(481)); // Obtain a Candle (Lumbridge West Swamp): Cave bug
		EXTRA_NPCS.put("obtain_ensouled_dog_head", ids(114)); // Obtain an Ensouled Dog Head (McGrubor's Woods): Guard dog
		EXTRA_NPCS.put("equip_black_mask", ids(1047, 1048, 1049, 1050, 1051)); // Equip a Black Mask (Mos Le'Harmless Cave): Cave horror
		EXTRA_NPCS.put("obtain_curved_bone", ids(648, 649, 650, 651, 652, 653, 654, 699, 700, 701, 702, 703, 704, 705, 787, 788, 789, 790, 791, 792, 793, 931, 932, 933, 934, 935, 936, 937, 938, 939, 940, 941, 942, 1047, 1048, 1049, 1050, 1051, 1877)); // Obtain a Curved Bone (Mos Le'Harmless Cave): Cave horror
		EXTRA_NPCS.put("obtain_long_bone", ids(648, 649, 650, 651, 652, 653, 654, 699, 700, 701, 702, 703, 704, 705, 787, 788, 789, 790, 791, 792, 793, 931, 932, 933, 934, 935, 936, 937, 938, 939, 940, 941, 942, 1047, 1048, 1049, 1050, 1051, 1877)); // Obtain a Long Bone (Mos Le'Harmless Cave): Cave horror
		EXTRA_NPCS.put("obtain_ensouled_horror_head", ids(1047, 1048, 1049, 1050, 1051)); // Obtain an Ensouled Horror Head (Mos Le'Harmless Cave): Cave horror
		EXTRA_NPCS.put("obtain_bearhead", ids(1377, 1378)); // Obtain a Bearhead (Mountain Camp): The Kendal
		EXTRA_NPCS.put("equip_dragon_full_helm", ids(2919)); // Equip a Dragon Full Helm (Otto's Grotto): Mithril dragon
		EXTRA_NPCS.put("equip_zamorak_monk_robe_top", ids(527, 528, 529)); // Equip a Zamorak Monk Robe Top (Paterdomus Temple): Monk of Zamorak
		EXTRA_NPCS.put("obtain_muddy_key", ids(291)); // Obtain a Muddy Key (Pirates' Hideout): Chaos dwarf
		EXTRA_NPCS.put("equip_dragon_2h_sword", ids(2054)); // Equip a Dragon 2h Sword (Rogues' Castle): Chaos Elemental
		EXTRA_NPCS.put("obtain_weapon_poison++", ids(2054)); // Obtain Weapon Poison++ (Rogues' Castle): Chaos Elemental
		EXTRA_NPCS.put("obtain_dragon_pickaxe", ids(239, 2054)); // Obtain a Dragon Pickaxe (Rogues' Castle): Chaos Elemental
		EXTRA_NPCS.put("obtain_larran's_key", ids(2005, 2006, 2007, 2008, 2018, 2054, 2075, 2076, 2077, 2078, 2079, 2080, 2081, 2082, 2083, 2084, 2085, 2086, 2087, 2088, 2089, 2841, 2842, 2851, 3016, 3022)); // Obtain a Larran's Key (Rogues' Castle): Chaos Elemental
		EXTRA_NPCS.put("obtain_slayer's_enchantment", ids(2005, 2006, 2007, 2008, 2018, 2054, 2075, 2076, 2077, 2078, 2079, 2080, 2081, 2082, 2083, 2084, 2085, 2086, 2087, 2088, 2089, 2841, 2842, 2851, 3016, 3022)); // Obtain a Slayer's Enchantment (Rogues' Castle): Chaos Elemental
		EXTRA_NPCS.put("equip_black_d'hide_vambraces", ids(498)); // Equip Black D'hide Vambraces (Smoke Devil Dungeon): Smoke devil
		EXTRA_NPCS.put("equip_dragon_chainbody", ids(423, 498, 499)); // Equip a Dragon Chainbody (Smoke Devil Dungeon): Smoke devil, Thermonuclear smoke devil
		EXTRA_NPCS.put("equip_smoke_battlestaff", ids(499)); // Equip a Smoke Battlestaff (Smoke Devil Dungeon): Thermonuclear smoke devil
		EXTRA_NPCS.put("equip_occult_necklace", ids(498, 499)); // Equip an Occult Necklace (Smoke Devil Dungeon): Smoke devil, Thermonuclear smoke devil
		EXTRA_NPCS.put("obtain_jar_of_smoke", ids(499)); // Obtain a Jar of Smoke (Smoke Devil Dungeon): Thermonuclear smoke devil
		EXTRA_NPCS.put("equip_rune_dagger", ids(260, 261, 262, 263, 264, 1047, 1048, 1049, 1050, 1051)); // Equip a Rune Dagger (The Green Coastline): Green dragon
		EXTRA_NPCS.put("equip_trident_of_the_seas", ids(492, 494)); // Equip a Trident of the Seas (The Kraken): Cave kraken, Kraken
		EXTRA_NPCS.put("obtain_jar_of_dirt", ids(494)); // Obtain a Jar of Dirt (The Kraken): Kraken
		EXTRA_NPCS.put("obtain_kraken_tentacle", ids(492, 494)); // Obtain a Kraken Tentacle (The Kraken): Cave kraken, Kraken
		EXTRA_NPCS.put("obtain_unicorn_horn", ids(2837)); // Obtain a Unicorn Horn (Varrock East Mine): Unicorn
		EXTRA_NPCS.put("obtain_ensouled_bear_head", ids(2839)); // Obtain an Ensouled Bear Head (Varrock East Mine): Black bear
		EXTRA_NPCS.put("obtain_ensouled_unicorn_head", ids(2837)); // Obtain an Ensouled Unicorn Head (Varrock East Mine): Unicorn
		EXTRA_NPCS.put("equip_dragon_defender", ids(2137, 2138, 2139, 2140, 2141, 2142)); // Equip a Dragon Defender (Warrior's Guild): Cyclops
		EXTRA_NPCS.put("equip_rune_defender", ids(2463, 2464, 2465, 2466, 2467, 2468)); // Equip a Rune Defender (Warrior's Guild): Cyclops
		EXTRA_NPCS.put("obtain_black_knife", ids(2137, 2138, 2139, 2140, 2141, 2142, 2235, 2236)); // Obtain a Black Knife (Warrior's Guild): Cyclops
		EXTRA_NPCS.put("obtain_warrior_guild_token", ids(2454, 2455, 2456)); // Obtain a Warrior Guild Token (Warrior's Guild): Animated Adamant Armour, Animated Mithril Armour, Animated Rune Armour
		EXTRA_NPCS.put("equip_dragon_boots", ids(2212, 2244, 3161)); // Equip Dragon Boots (Wilderness Godwars Dungeon): Spiritual mage
		EXTRA_NPCS.put("obtain_ecumenical_key", ids(2212, 2244, 3161)); // Obtain an Ecumenical Key (Wilderness Godwars Dungeon): Spiritual mage
		EXTRA_NPCS.put("equip_abyssal_whip", ids(415, 416)); // Equip an Abyssal Whip (Wilderness Slayer Cave): Abyssal demon
		EXTRA_NPCS.put("obtain_ensouled_dragon_head", ids(260, 261, 262, 263, 264, 2918)); // Obtain an Ensouled Dragon Head (Wilderness Slayer Cave): Brutal green dragon, Green dragon

		// --- Real level requirements (equip gear from the item database; Rigour and Augury) ---
		// Tasks needing two skills: the data lists both, but only the first is read.
		REQUIREMENTS.put("build_spirit_tree", levels(Skill.CONSTRUCTION, 70, Skill.FARMING, 83)); // Build a Spirit Tree
		REQUIREMENTS.put("mine_dense_essence_blocks", levels(Skill.MINING, 38, Skill.CRAFTING, 38)); // Mine some Dense Essence Blocks
		REQUIREMENTS.put("catch_bluegill", levels(Skill.FISHING, 43, Skill.HUNTER, 35)); // Catch a Bluegill
		REQUIREMENTS.put("catch_common_tench", levels(Skill.FISHING, 56, Skill.HUNTER, 51)); // Catch a Common Tench
		REQUIREMENTS.put("catch_mottled_eel", levels(Skill.FISHING, 73, Skill.HUNTER, 68)); // Catch a Mottled Eel
		REQUIREMENTS.put("catch_greater_siren", levels(Skill.FISHING, 91, Skill.HUNTER, 87)); // Catch a Greater Siren
		// Filed under "Combat" with its level, so it isn't checked as Slayer.
		REQUIREMENTS.put("defeat_aberrant_spectre_fast", levels(Skill.SLAYER, 60)); // Defeat an Aberrant Spectre in 18 Seconds
		REQUIREMENTS.put("mix_combat_potions", levels(Skill.HERBLORE, 36)); // Mix some Combat Potions (no level in the task data)
		REQUIREMENTS.put("gwd_nex_mix_ancient_brew", levels(Skill.HERBLORE, 85)); // Mix an Ancient Brew (no level in the task data)
		REQUIREMENTS.put("tob_mix_bastion_potion", levels(Skill.HERBLORE, 80)); // Mix a Bastion Potion (no level in the task data)
		REQUIREMENTS.put("toa_mix_menaphite_remedy", levels(Skill.HERBLORE, 88)); // Mix a Menaphite Remedy (no level in the task data)
		REQUIREMENTS.put("cox_activate_augury", levels(Skill.PRAYER, 77, Skill.DEFENCE, 70)); // Activate Augury
		REQUIREMENTS.put("cox_activate_rigour", levels(Skill.PRAYER, 74, Skill.DEFENCE, 70)); // Activate Rigour
		REQUIREMENTS.put("gwd_graardor_equip_boots", levels(Skill.DEFENCE, 65)); // Equip Bandos Boots
		REQUIREMENTS.put("gwd_graardor_equip_tassets", levels(Skill.DEFENCE, 65)); // Equip Bandos Tassets
		REQUIREMENTS.put("equip_bryophytas_staff", levels(Skill.ATTACK, 30, Skill.MAGIC, 30)); // Equip Bryophyta's Staff
		REQUIREMENTS.put("corrupted_gauntlet_equip_crystal_legs", levels(Skill.DEFENCE, 70)); // Equip Crystal Legs
		REQUIREMENTS.put("equip_decorative_armour_(white_platebody)", levels(Skill.DEFENCE, 5)); // Equip Decorative Armour (White Platebody)
		REQUIREMENTS.put("equip_decorative_sword_(gold)", levels(Skill.ATTACK, 5)); // Equip Decorative Sword (Gold)
		REQUIREMENTS.put("equip_decorative_sword_(white)", levels(Skill.ATTACK, 5)); // Equip Decorative Sword (White)
		REQUIREMENTS.put("cox_equip_dinhs_bulwark", levels(Skill.ATTACK, 75, Skill.DEFENCE, 75)); // Equip Dinh's Bulwark
		REQUIREMENTS.put("cox_equip_dragon_claws", levels(Skill.ATTACK, 60)); // Equip Dragon Claws
		REQUIREMENTS.put("equip_dragon_platelegs", levels(Skill.DEFENCE, 60)); // Equip Dragon Platelegs
		REQUIREMENTS.put("equip_ferocious_gloves", levels(Skill.ATTACK, 80, Skill.DEFENCE, 80)); // Equip Ferocious Gloves
		REQUIREMENTS.put("equip_iban's_staff", levels(Skill.MAGIC, 50, Skill.ATTACK, 50)); // Equip Iban's Staff
		REQUIREMENTS.put("equip_iban's_staff(u)", levels(Skill.ATTACK, 50, Skill.MAGIC, 50)); // Equip Iban's Staff(u)
		REQUIREMENTS.put("equip_proselyte_tassets_or_cuisse", levels(Skill.DEFENCE, 30, Skill.PRAYER, 20)); // Equip Proselyte Tassets or Cuisse
		REQUIREMENTS.put("equip_ranger_boots", levels(Skill.RANGED, 40)); // Equip Ranger Boots
		REQUIREMENTS.put("equip_red_d'hide_vambraces", levels(Skill.RANGED, 60)); // Equip Red D'Hide Vambrances
		REQUIREMENTS.put("equip_void_knight_gloves", levels(Skill.ATTACK, 42, Skill.STRENGTH, 42, Skill.HITPOINTS, 42, Skill.RANGED, 42, Skill.MAGIC, 42, Skill.DEFENCE, 42, Skill.PRAYER, 22)); // Equip Void Knight Gloves
		REQUIREMENTS.put("gwd_graardor_equip_chestplate", levels(Skill.DEFENCE, 65)); // Equip a Bandos Chestplate
		REQUIREMENTS.put("gwd_graardor_equip_bgs", levels(Skill.ATTACK, 75)); // Equip a Bandos Godsword
		REQUIREMENTS.put("equip_barrelchest_anchor", levels(Skill.ATTACK, 60, Skill.STRENGTH, 40)); // Equip a Barrelchest Anchor
		REQUIREMENTS.put("equip_beginner_wand", levels(Skill.MAGIC, 45)); // Equip a Beginner Wand
		REQUIREMENTS.put("equip_black_battleaxe", levels(Skill.ATTACK, 10)); // Equip a Black Battleaxe
		REQUIREMENTS.put("equip_black_d'hide_body", levels(Skill.RANGED, 70, Skill.DEFENCE, 40)); // Equip a Black D'hide Body
		REQUIREMENTS.put("equip_black_sq_shield", levels(Skill.DEFENCE, 10)); // Equip a Black Square Shield
		REQUIREMENTS.put("equip_brine_sabre", levels(Skill.ATTACK, 40)); // Equip a Brine Sabre
		REQUIREMENTS.put("equip_coif", levels(Skill.RANGED, 20)); // Equip a Coif
		REQUIREMENTS.put("corrupted_gauntlet_equip_crystal_body", levels(Skill.DEFENCE, 70)); // Equip a Crystal Body
		REQUIREMENTS.put("equip_crystal_bow", levels(Skill.RANGED, 70, Skill.AGILITY, 50)); // Equip a Crystal Bow
		REQUIREMENTS.put("equip_crystal_halberd", levels(Skill.ATTACK, 70, Skill.AGILITY, 50, Skill.STRENGTH, 35)); // Equip a Crystal Halberd
		REQUIREMENTS.put("corrupted_gauntlet_equip_crystal_helm", levels(Skill.DEFENCE, 70)); // Equip a Crystal Helm
		REQUIREMENTS.put("equip_crystal_shield", levels(Skill.DEFENCE, 70, Skill.AGILITY, 50)); // Equip a Crystal Shield
		REQUIREMENTS.put("equip_dagon'hai_hat", levels(Skill.MAGIC, 70, Skill.DEFENCE, 40)); // Equip a Dagon'hai Hat
		REQUIREMENTS.put("equip_dagon'hai_robe_bottom", levels(Skill.MAGIC, 70, Skill.DEFENCE, 40)); // Equip a Dagon'hai Robe Bottom
		REQUIREMENTS.put("equip_dagon'hai_robe_top", levels(Skill.MAGIC, 70, Skill.DEFENCE, 40)); // Equip a Dagon'hai Robe Top
		REQUIREMENTS.put("equip_dragon_chainbody", levels(Skill.DEFENCE, 60)); // Equip a Dragon Chainbody
		REQUIREMENTS.put("equip_dragon_halberd", levels(Skill.ATTACK, 60, Skill.STRENGTH, 30)); // Equip a Dragon Halberd
		REQUIREMENTS.put("cox_equip_dh_crossbow", levels(Skill.RANGED, 65)); // Equip a Dragon Hunter Crossbow
		REQUIREMENTS.put("equip_dragon_longsword", levels(Skill.ATTACK, 60)); // Equip a Dragon Longsword
		REQUIREMENTS.put("equip_dragon_plateskirt", levels(Skill.DEFENCE, 60)); // Equip a Dragon Plateskirt
		REQUIREMENTS.put("vorkath_equip_dragonbone_necklace", levels(Skill.PRAYER, 80)); // Equip a Dragonbone Necklace
		REQUIREMENTS.put("vorkath_equip_dragonfire_ward", levels(Skill.DEFENCE, 75, Skill.RANGED, 70)); // Equip a Dragonfire Ward
		REQUIREMENTS.put("equip_granite_body", levels(Skill.STRENGTH, 50, Skill.DEFENCE, 50)); // Equip a Granite Body
		REQUIREMENTS.put("equip_granite_helm", levels(Skill.STRENGTH, 50, Skill.DEFENCE, 50)); // Equip a Granite Helm
		REQUIREMENTS.put("equip_granite_longsword", levels(Skill.ATTACK, 50, Skill.STRENGTH, 50)); // Equip a Granite Longsword
		REQUIREMENTS.put("equip_granite_shield", levels(Skill.DEFENCE, 50, Skill.STRENGTH, 50)); // Equip a Granite Shield
		REQUIREMENTS.put("equip_guthix_cape", levels(Skill.MAGIC, 50)); // Equip a Guthix Cape
		REQUIREMENTS.put("equip_hill_giant_club", levels(Skill.ATTACK, 40)); // Equip a Hill Giant Club
		REQUIREMENTS.put("equip_keris", levels(Skill.ATTACK, 50)); // Equip a Keris
		REQUIREMENTS.put("cox_equip_kodai_wand", levels(Skill.MAGIC, 75)); // Equip a Kodai Wand
		REQUIREMENTS.put("equip_leaf-bladed_battleaxe", levels(Skill.ATTACK, 65, Skill.SLAYER, 55)); // Equip a Leaf-bladed Battleaxe
		REQUIREMENTS.put("equip_leaf-bladed_sword", levels(Skill.SLAYER, 55, Skill.ATTACK, 50)); // Equip a Leaf-bladed Sword
		REQUIREMENTS.put("equip_light_mystic_hat", levels(Skill.MAGIC, 40, Skill.DEFENCE, 20)); // Equip a Light Mystic Hat
		REQUIREMENTS.put("equip_mage's_book", levels(Skill.MAGIC, 60)); // Equip a Mage's Book
		REQUIREMENTS.put("equip_master_wand", levels(Skill.MAGIC, 60)); // Equip a Master Wand
		REQUIREMENTS.put("equip_max_cape", levels(Skill.ATTACK, 99, Skill.STRENGTH, 99, Skill.DEFENCE, 99, Skill.HITPOINTS, 99, Skill.RANGED, 99, Skill.PRAYER, 99, Skill.MAGIC, 99, Skill.COOKING, 99, Skill.WOODCUTTING, 99, Skill.FLETCHING, 99, Skill.FISHING, 99, Skill.FIREMAKING, 99, Skill.CRAFTING, 99, Skill.SMITHING, 99, Skill.MINING, 99, Skill.HERBLORE, 99, Skill.AGILITY, 99, Skill.THIEVING, 99, Skill.SLAYER, 99, Skill.FARMING, 99, Skill.RUNECRAFT, 99, Skill.HUNTER, 99, Skill.CONSTRUCTION, 99)); // Equip a Max Cape
		REQUIREMENTS.put("equip_mithril_mace_sword", levels(Skill.ATTACK, 20)); // Equip a Mithirl Mace
		REQUIREMENTS.put("equip_mithril_2h_sword", levels(Skill.ATTACK, 20)); // Equip a Mithril 2H Sword
		REQUIREMENTS.put("equip_mithril_sword", levels(Skill.ATTACK, 20)); // Equip a Mithril Sword
		REQUIREMENTS.put("dagannoth_kings_equip_mud_battlestaff", levels(Skill.ATTACK, 30, Skill.MAGIC, 30)); // Equip a Mud Battlestaff
		REQUIREMENTS.put("equip_neitiznot_shield", levels(Skill.DEFENCE, 30)); // Equip a Neitiznot Shield
		REQUIREMENTS.put("equip_penance_skirt", levels(Skill.RANGED, 60, Skill.DEFENCE, 40)); // Equip a Penance Skirt
		REQUIREMENTS.put("equip_proselyte_hauberk", levels(Skill.DEFENCE, 30, Skill.PRAYER, 20)); // Equip a Proselyte Hauberk
		REQUIREMENTS.put("equip_proselyte_sallet", levels(Skill.DEFENCE, 30, Skill.PRAYER, 20)); // Equip a Proselyte Sallet
		REQUIREMENTS.put("equip_red_d'hide_body", levels(Skill.RANGED, 60, Skill.DEFENCE, 40)); // Equip a Red D'hide Body
		REQUIREMENTS.put("equip_rune_defender", levels(Skill.ATTACK, 40, Skill.DEFENCE, 40)); // Equip a Rune Defender
		REQUIREMENTS.put("equip_rune_hasta", levels(Skill.ATTACK, 40)); // Equip a Rune Hasta
		REQUIREMENTS.put("equip_rune_pickaxe", levels(Skill.MINING, 41, Skill.ATTACK, 40)); // Equip a Rune Pickaxe
		REQUIREMENTS.put("equip_rune_square_shield", levels(Skill.DEFENCE, 40)); // Equip a Rune Square Shield
		REQUIREMENTS.put("equip_saradomin_cape", levels(Skill.MAGIC, 50)); // Equip a Saradomin Cape
		REQUIREMENTS.put("gwd_zilyana_equip_sgs", levels(Skill.ATTACK, 75)); // Equip a Saradomin Godsword
		REQUIREMENTS.put("gwd_zilyana_equip_sara_sword", levels(Skill.ATTACK, 70)); // Equip a Saradomin Sword
		REQUIREMENTS.put("dagannoth_kings_equip_seercull", levels(Skill.RANGED, 50)); // Equip a Seercull
		REQUIREMENTS.put("equip_smoke_battlestaff", levels(Skill.ATTACK, 30, Skill.MAGIC, 30)); // Equip a Smoke Battlestaff
		REQUIREMENTS.put("equip_spirit_shield", levels(Skill.PRAYER, 55, Skill.DEFENCE, 45)); // Equip a Spirit Shield
		REQUIREMENTS.put("equip_splitbark_body", levels(Skill.MAGIC, 40, Skill.DEFENCE, 40)); // Equip a Splitbark Body
		REQUIREMENTS.put("equip_splitbark_legs", levels(Skill.MAGIC, 40, Skill.DEFENCE, 40)); // Equip a Splitbark Legs
		REQUIREMENTS.put("gwd_kril_equip_staff_of_light", levels(Skill.MAGIC, 75, Skill.ATTACK, 75)); // Equip a Staff of Light
		REQUIREMENTS.put("gwd_kril_equip_staff_of_the_dead", levels(Skill.ATTACK, 75, Skill.MAGIC, 75)); // Equip a Staff of the Dead
		REQUIREMENTS.put("gwd_kril_equip_steam_battlestaff", levels(Skill.ATTACK, 30, Skill.MAGIC, 30)); // Equip a Steam Battlestaff
		REQUIREMENTS.put("equip_steel_longsword", levels(Skill.ATTACK, 5)); // Equip a Steel Longsword
		REQUIREMENTS.put("equip_steel_med_helm", levels(Skill.DEFENCE, 5)); // Equip a Steel Med Helm
		REQUIREMENTS.put("equip_studded_body", levels(Skill.RANGED, 20, Skill.DEFENCE, 20)); // Equip a Studded Body
		REQUIREMENTS.put("equip_teacher_wand", levels(Skill.MAGIC, 50)); // Equip a Teacher Wand
		REQUIREMENTS.put("cox_equip_twisted_bow", levels(Skill.RANGED, 75)); // Equip a Twisted Bow
		REQUIREMENTS.put("cox_equip_twisted_buckler", levels(Skill.DEFENCE, 75, Skill.RANGED, 75)); // Equip a Twisted Buckler
		REQUIREMENTS.put("equip_void_knight_mace", levels(Skill.ATTACK, 42, Skill.STRENGTH, 42, Skill.HITPOINTS, 42, Skill.RANGED, 42, Skill.MAGIC, 42, Skill.DEFENCE, 42, Skill.PRAYER, 22)); // Equip a Void Knight Mace
		REQUIREMENTS.put("equip_void_knight_robe", levels(Skill.ATTACK, 42, Skill.STRENGTH, 42, Skill.HITPOINTS, 42, Skill.RANGED, 42, Skill.MAGIC, 42, Skill.DEFENCE, 42, Skill.PRAYER, 22)); // Equip a Void Knight Robe
		REQUIREMENTS.put("equip_void_knight_top", levels(Skill.ATTACK, 42, Skill.STRENGTH, 42, Skill.HITPOINTS, 42, Skill.RANGED, 42, Skill.MAGIC, 42, Skill.DEFENCE, 42, Skill.PRAYER, 22)); // Equip a Void Knight Top
		REQUIREMENTS.put("equip_void_mage_helm", levels(Skill.ATTACK, 42, Skill.STRENGTH, 42, Skill.HITPOINTS, 42, Skill.RANGED, 42, Skill.MAGIC, 42, Skill.DEFENCE, 42, Skill.PRAYER, 22)); // Equip a Void Mage Helm
		REQUIREMENTS.put("equip_void_melee_helm", levels(Skill.ATTACK, 42, Skill.STRENGTH, 42, Skill.HITPOINTS, 42, Skill.RANGED, 42, Skill.MAGIC, 42, Skill.DEFENCE, 42, Skill.PRAYER, 22)); // Equip a Void Melee Helm
		REQUIREMENTS.put("equip_void_ranger_helm", levels(Skill.ATTACK, 42, Skill.STRENGTH, 42, Skill.HITPOINTS, 42, Skill.RANGED, 42, Skill.MAGIC, 42, Skill.DEFENCE, 42, Skill.PRAYER, 22)); // Equip a Void Ranger Helm
		REQUIREMENTS.put("equip_xerician_hat", levels(Skill.MAGIC, 20, Skill.DEFENCE, 10)); // Equip a Xerician Hat
		REQUIREMENTS.put("equip_xerician_robe", levels(Skill.MAGIC, 20, Skill.DEFENCE, 10)); // Equip a Xerician Robe
		REQUIREMENTS.put("equip_xerician_top", levels(Skill.MAGIC, 20, Skill.DEFENCE, 10)); // Equip a Xerician Top
		REQUIREMENTS.put("equip_zamorak_cape", levels(Skill.MAGIC, 50)); // Equip a Zamorak Cape
		REQUIREMENTS.put("gwd_kril_equip_zgs", levels(Skill.ATTACK, 75)); // Equip a Zamorak Godsword
		REQUIREMENTS.put("gwd_kril_equip_zammy_spear", levels(Skill.ATTACK, 70)); // Equip a Zamorakian Spear
		REQUIREMENTS.put("equip_adamant_battleaxe", levels(Skill.ATTACK, 30)); // Equip an Adamant Battleaxe
		REQUIREMENTS.put("equip_adamant_mace_sword", levels(Skill.ATTACK, 30)); // Equip an Adamant Mace
		REQUIREMENTS.put("cox_equip_ancestral_hat", levels(Skill.MAGIC, 75, Skill.DEFENCE, 65)); // Equip an Ancestral Hat
		REQUIREMENTS.put("cox_equip_ancestral_bottom", levels(Skill.MAGIC, 75, Skill.DEFENCE, 65)); // Equip an Ancestral Robe Bottom
		REQUIREMENTS.put("cox_equip_ancestral_top", levels(Skill.MAGIC, 75, Skill.DEFENCE, 65)); // Equip an Ancestral Robe Top
		REQUIREMENTS.put("equip_ancient_staff", levels(Skill.MAGIC, 50, Skill.ATTACK, 50)); // Equip an Ancient Staff
		REQUIREMENTS.put("equip_ancient_wyvern_shield", levels(Skill.DEFENCE, 75, Skill.MAGIC, 70)); // Equip an Ancient Wyvern Shield
		REQUIREMENTS.put("equip_apprentice_wand", levels(Skill.MAGIC, 50)); // Equip an Apprentice Wand
		REQUIREMENTS.put("gwd_kreearra_equip_chainskirt", levels(Skill.DEFENCE, 70, Skill.RANGED, 70)); // Equip an Armadyl Chainskirt
		REQUIREMENTS.put("gwd_kreearra_equip_chestplate", levels(Skill.DEFENCE, 70, Skill.RANGED, 70)); // Equip an Armadyl Chestplate
		REQUIREMENTS.put("gwd_zilyana_equip_acb", levels(Skill.RANGED, 70)); // Equip an Armadyl Crossbow
		REQUIREMENTS.put("gwd_kreearra_equip_ags", levels(Skill.ATTACK, 75)); // Equip an Armadyl Godsword
		REQUIREMENTS.put("gwd_kreearra_equip_helm", levels(Skill.DEFENCE, 70, Skill.RANGED, 70)); // Equip an Armadyl Helmet
		REQUIREMENTS.put("vorkath_equip_avas_assembler", levels(Skill.RANGED, 70)); // Equip an Ava's Assembler
		REQUIREMENTS.put("tob_equip_avernic_defender", levels(Skill.ATTACK, 70, Skill.DEFENCE, 70)); // Equip an Avernic Defender
		REQUIREMENTS.put("cox_equip_elder_maul", levels(Skill.ATTACK, 75, Skill.STRENGTH, 75)); // Equip an Elder Maul
		REQUIREMENTS.put("equip_elite_void_knight_robe", levels(Skill.ATTACK, 42, Skill.STRENGTH, 42, Skill.HITPOINTS, 42, Skill.RANGED, 42, Skill.MAGIC, 42, Skill.DEFENCE, 42, Skill.PRAYER, 22)); // Equip an Elite Void Knight Robe
		REQUIREMENTS.put("equip_elite_void_knight_top", levels(Skill.ATTACK, 42, Skill.STRENGTH, 42, Skill.HITPOINTS, 42, Skill.RANGED, 42, Skill.MAGIC, 42, Skill.DEFENCE, 42, Skill.PRAYER, 22)); // Equip an Elite Void Knight Top
		REQUIREMENTS.put("equip_imbued_guthix_cape", levels(Skill.MAGIC, 50)); // Equip an Imbued Guthix Cape
		REQUIREMENTS.put("equip_imbued_max_cape", levels(Skill.ATTACK, 99, Skill.STRENGTH, 99, Skill.DEFENCE, 99, Skill.HITPOINTS, 99, Skill.RANGED, 99, Skill.PRAYER, 99, Skill.MAGIC, 99, Skill.COOKING, 99, Skill.WOODCUTTING, 99, Skill.FLETCHING, 99, Skill.FISHING, 99, Skill.FIREMAKING, 99, Skill.CRAFTING, 99, Skill.SMITHING, 99, Skill.MINING, 99, Skill.HERBLORE, 99, Skill.AGILITY, 99, Skill.THIEVING, 99, Skill.SLAYER, 99, Skill.FARMING, 99, Skill.RUNECRAFT, 99, Skill.HUNTER, 99, Skill.CONSTRUCTION, 99)); // Equip an Imbued Max Cape
		REQUIREMENTS.put("equip_imbued_saradomin_cape", levels(Skill.MAGIC, 50)); // Equip an Imbued Saradomin Cape
		REQUIREMENTS.put("equip_imbued_zamorak_cape", levels(Skill.MAGIC, 50)); // Equip an Imbued Zamorak Cape
		REQUIREMENTS.put("equip_infernal_max_cape", levels(Skill.ATTACK, 99, Skill.STRENGTH, 99, Skill.DEFENCE, 99, Skill.HITPOINTS, 99, Skill.RANGED, 99, Skill.PRAYER, 99, Skill.MAGIC, 99, Skill.COOKING, 99, Skill.WOODCUTTING, 99, Skill.FLETCHING, 99, Skill.FISHING, 99, Skill.FIREMAKING, 99, Skill.CRAFTING, 99, Skill.SMITHING, 99, Skill.MINING, 99, Skill.HERBLORE, 99, Skill.AGILITY, 99, Skill.THIEVING, 99, Skill.SLAYER, 99, Skill.FARMING, 99, Skill.RUNECRAFT, 99, Skill.HUNTER, 99, Skill.CONSTRUCTION, 99)); // Equip an Infernal Max Cape
		REQUIREMENTS.put("barrows_equip_ahrims_set", levels(Skill.MAGIC, 70, Skill.DEFENCE, 70)); // Equip an Undamaged Ahrim's Set
		REQUIREMENTS.put("barrows_equip_dharoks_set", levels(Skill.DEFENCE, 70)); // Equip an Undamaged Dharok's Set
		REQUIREMENTS.put("barrows_equip_guthans_set", levels(Skill.DEFENCE, 70)); // Equip an Undamaged Guthan's Set
		REQUIREMENTS.put("barrows_equip_karils_set", levels(Skill.RANGED, 70)); // Equip an Undamaged Karil's Set
		REQUIREMENTS.put("barrows_equip_torags_set", levels(Skill.DEFENCE, 70)); // Equip an Undamaged Torag's Set
		REQUIREMENTS.put("barrows_equip_veracs_set", levels(Skill.DEFENCE, 70)); // Equip an Undamaged Verac's Set
		REQUIREMENTS.put("barrows_equip_any_piece", levels(Skill.DEFENCE, 70)); // Equip any piece of Undamaged Barrows Gear
		REQUIREMENTS.put("phosani_equip_eldritch_staff", levels(Skill.MAGIC, 75, Skill.HITPOINTS, 50)); // Equip the Eldritch Nightmare Staff
		REQUIREMENTS.put("tob_equip_ghrazi_rapier", levels(Skill.ATTACK, 75)); // Equip the Ghrazi Rapier
		REQUIREMENTS.put("phosani_equip_harmonised_staff", levels(Skill.MAGIC, 75, Skill.HITPOINTS, 50)); // Equip the Harmonised Nightmare Staff
		REQUIREMENTS.put("phosani_equip_inq_helm", levels(Skill.STRENGTH, 70, Skill.DEFENCE, 30)); // Equip the Inquisitor's Great Helm
		REQUIREMENTS.put("phosani_equip_inq_hauberk", levels(Skill.STRENGTH, 70, Skill.DEFENCE, 30)); // Equip the Inquisitor's Hauberk
		REQUIREMENTS.put("phosani_equip_inq_mace", levels(Skill.ATTACK, 75)); // Equip the Inquisitor's Mace
		REQUIREMENTS.put("phosani_equip_inq_plateskirt", levels(Skill.STRENGTH, 70, Skill.DEFENCE, 30)); // Equip the Inquisitor's Plateskirt
		REQUIREMENTS.put("tob_equip_justiciar_chestguard", levels(Skill.DEFENCE, 75)); // Equip the Justiciar Chestguard
		REQUIREMENTS.put("tob_equip_justiciar_faceguard", levels(Skill.DEFENCE, 75)); // Equip the Justiciar Faceguard
		REQUIREMENTS.put("tob_equip_justiciar_legguards", levels(Skill.DEFENCE, 75)); // Equip the Justiciar Legguards
		REQUIREMENTS.put("phosani_equip_nightmare_staff", levels(Skill.MAGIC, 65, Skill.HITPOINTS, 50)); // Equip the Nightmare Staff
		REQUIREMENTS.put("tob_equip_sanguinesti_staff", levels(Skill.MAGIC, 75)); // Equip the Sanguinesti Staff
		REQUIREMENTS.put("sarachnis_equip_cudgel", levels(Skill.ATTACK, 65)); // Equip the Sarachnis Cudgel
		REQUIREMENTS.put("tob_equip_scythe_of_vitur", levels(Skill.ATTACK, 75, Skill.STRENGTH, 75)); // Equip the Scythe of Vitur
		REQUIREMENTS.put("zulrah_equip_serpent_helm", levels(Skill.DEFENCE, 75)); // Equip the Serpent Helm
		REQUIREMENTS.put("zulrah_equip_blowpipe", levels(Skill.RANGED, 75)); // Equip the Toxic Blowpipe
		REQUIREMENTS.put("zulrah_equip_trident", levels(Skill.MAGIC, 75)); // Equip the Trident of the Swamp
		REQUIREMENTS.put("phosani_equip_volatile_staff", levels(Skill.MAGIC, 75, Skill.HITPOINTS, 50)); // Equip the Volatile Nightmare Staff

		// --- Agility shortcuts and courses ---
		// Filled from RuneLite's own lists (AgilityShortcut and the agility plugin's course
		// obstacles), matched by level, location and name.
		EXTRA_OBJECTS.put("agility_level_26_underwall_tunnel", ids(16527, 16528)); // Use the Level 26 Agility Underwall Tunnel (Air Altar, 11827) [RuneLite shortcut FALADOR_UNDERWALL_TUNNEL]
		EXTRA_OBJECTS.put("Al_Kharid_mine_rocks_shortcut", ids(16549, 16550)); // Use Level 38 Agility Al Kharid Mine Rocks Shortcut (Al Kharid Mine, 13107) [RuneLite shortcut AL_KHARID_MINING_PITCLIFF_SCRAMBLE]
		EXTRA_OBJECTS.put("agility_level_59_arandar_rocks", ids(16514, 16515)); // Use the Level 59 Agility Arandar Rocks Shortcut (Arandar Pass, 9267) [RuneLite shortcut ELVEN_OVERPASS_CLIFF_SCRAMBLE]
		EXTRA_OBJECTS.put("agility_level_33_ardougne_log_balance", ids(16546, 16547, 16548)); // Use the Level 33 Ardougne Log Balance (Ardougne Castle, 10291) [RuneLite shortcut ARDOUGNE_LOG_BALANCE]
		EXTRA_OBJECTS.put("complete_ardougne_roof", ids(15608, 15609, 26635, 15610, 15611, 28912, 15612)); // Use the Ardougne Rooftop Course (Ardougne Market, 10547) [RuneLite course Ardougne]
		EXTRA_OBJECTS.put("agility_level_45_log_balance_auburnvale", ids(56991)); // Use the Level 45 Agility Log Balance in Auburnvale (Auburnvale East Bank, 5684) [RuneLite shortcut AUBURN_VALLEY_LOG_BALANCE_NORTH]
		EXTRA_OBJECTS.put("complete_barbarian_outpost_course", ids(23131, 23144, 20211, 23547, 42487, 1948)); // Use the Barbarian Outpost Course (Barbarian Outpost, 10039) [RuneLite course Barbarian]
		EXTRA_OBJECTS.put("agility_red_dragon_stepping_stones", ids(19040)); // Use the Level 56 Agility Brimhaven Dungeon Stepping Stones (Brimhaven Dungeon, 10801) [RuneLite shortcut BRIMHAVEN_DUNGEON_EAST_STEPPING_STONES]
		EXTRA_OBJECTS.put("use_burgh_de_rott_low_fence_25", ids(12776)); // Use the Level 25 Agility Burgh de Rott Low Fence (Burgh de Rott, 13874) [RuneLite shortcut BURGH_AGILITY_SHORTCUT_FENCE]
		EXTRA_OBJECTS.put("agility_canifis_rooftop_course", ids(14843, 14844, 14845, 14848, 14846, 14894, 14847, 14897)); // Use the Canifis Rooftop Course (Canifis, 13878) [RuneLite course Canifis]
		EXTRA_OBJECTS.put("agility_level_43_agility_rock_climb_capybara_picnic", ids(57604, 57605)); // Use the Level 43 Agility Rock Climb Shortcut to the Capybara Picnic (Capybara Picnic, 4910) [RuneLite shortcut TLATI_RAINFORST_CLIFF_SCRAMBLE]
		EXTRA_OBJECTS.put("agility_level_32_catherby_cliff", ids(17042)); // Use the Level 32 Agility Crossbow Catherby Cliff Shortcut (Catherby Shore, 11317) [RuneLite shortcut CATHERBY_CLIFFSIDE_GRAPPLE]
		EXTRA_OBJECTS.put("agility_level_36_water_obelisk_crossbow_shortcut", ids(17062)); // Use the Level 36 Agility Water Obelisk Crossbow Shorcut (Catherby Shore, 11317) [RuneLite shortcut CATHERBY_OBELISK_GRAPPLE]
		EXTRA_OBJECTS.put("agility_stepping_stones_champion_guild", ids(16533)); // Use the Level 31 Agility Stepping Stone at the Champion's Guild (Champions' Guild, 12596) [RuneLite shortcut DRAYNOR_MANOR_STEPPING_STONES]
		EXTRA_OBJECTS.put("agility_level_20_coal_truck_log_balance", ids(23274)); // Use the Level 20 Coal Truck Log Balance (Coal Trucks, 10294) [RuneLite shortcut COAL_TRUCKS_LOG_BALANCE]
		EXTRA_OBJECTS.put("agility_level_62_path_colossal_wyrm_course", ids(55191, 55192, 55194)); // Take the Advanced Path at the Colossal Wyrm Agility Course (Colossal Wyrm Remains, 6445) [RuneLite course Colossal Wyrm advanced]
		EXTRA_OBJECTS.put("agility_level_50_path_colossal_wyrm_course", ids(55184, 55186, 55190)); // Take the Basic Path at the Colossal Wyrm Agility Course (Colossal Wyrm Remains, 6445) [RuneLite course Colossal Wyrm basic]
		EXTRA_OBJECTS.put("agility_level_15_pillar_jump_corsair_cove_dungeon", ids(31809)); // Use the Level 15 Agility Pillar Jump in Corsair Cove Dungeon (Corsair Cove Resource Area, 9773) [RuneLite shortcut CORSAIR_COVE_DUNGEON_PILLAR]
		EXTRA_OBJECTS.put("agility_level_30_rock_climb_corsair_cove_resource_area", ids(31758, 31759)); // Use the Level 30 Agility Rock Climb Corsair Cove Resource Area (Corsair Cove Resource Area, 9773) [RuneLite shortcut CORSAIR_COVE_RESOURCE_ROCKS]
		EXTRA_OBJECTS.put("agility_level_18_crabclaw_caves_crevice", ids(31695, 31696)); // Use the Level 18 Agility Crabclaw Caves Crevice (Crabclaw Caves, 6453) [RuneLite shortcut CRABCLAW_CAVES_CREVICE]
		EXTRA_OBJECTS.put("agility_level_49_dark_essence_mine_rock_climb", ids(27990)); // Use the Level 49 Agility Dark Essence Mine Boulder Jump (Dark Essence Mine, 6972) [RuneLite shortcut ARCEUUS_ESSENCE_MINE_BOULDER]
		EXTRA_OBJECTS.put("agility_level_52_dark_essence_mine_rock_climb", ids(27987, 27988)); // Use the Level 52 Agility Dark Essence Mine Rock Climb (Dark Essence Mine, 6972) [RuneLite shortcut ARCEUUS_ESSENCE_MINE_EAST_SCRAMBLE]
		EXTRA_OBJECTS.put("agility_level_69_dark_essence_mine_rock_climb", ids(34741)); // Use the Level 69 Agility Dark Essence Mine Rock Climb (Dark Essence Mine, 6972) [RuneLite shortcut ARCEUUS_ESSENSE_NORTH]
		EXTRA_OBJECTS.put("agility_level_73_dark_essence_mine_rock_climb", ids(27984, 27985)); // Use the Level 73 Agility Dark Essence Mine Rock Climb (Dark Essence Mine, 6972) [RuneLite shortcut ARCEUUS_ESSENSE_MINE_WEST]
		EXTRA_OBJECTS.put("agility_fence_jump", ids(16518)); // Use the Level 13 Agility Varrock Fence Jump Shortcut (Dark Wizards' Stone Table, 12852) [RuneLite shortcut VARROCK_SOUTH_FENCE]
		EXTRA_OBJECTS.put("complete_draynor_roof", ids(11404, 11405, 11406, 11430, 11630, 11631, 11632, 7527)); // Use the Draynor Rooftop Course (Draynor North, 12339) [RuneLite course Draynor]
		EXTRA_OBJECTS.put("agility_level_25_eagle's_peak", ids(19849)); // Use the Level 25 Agility Eagle's Peak Shortcut (Eagles' Peak, 9270) [RuneLite shortcut EAGLES_PEAK_ROCK_CLIMB]
		EXTRA_OBJECTS.put("complete_falador_roof", ids(14898, 14899, 14901, 14903, 14904, 14905, 14911, 14919, 14920, 14921, 14922, 14923, 14924, 14925)); // Use the Falador Rooftop Course (East Falador, 12084) [RuneLite course Falador]
		EXTRA_OBJECTS.put("agility_level_16_underwall_yanille_shortcut", ids(16519, 16520)); // Use the Level 16 Agility Underwall Yanille Shortcut (East Yanille, 10288) [RuneLite shortcut YANILLE_UNDERWALL_TUNNEL]
		EXTRA_OBJECTS.put("agility_level_72_agility_stepping_stone_chaos_temple", ids(53237)); // Use the Level 72 Agility Chaos Temple Stepping Stone (Ents' Trail, 13112) [RuneLite shortcut CHAOS_TEMPLE_STEPPING_STONE]
		EXTRA_OBJECTS.put("agility_level_55_etceteria_stepping_stone", ids(11768)); // Use the Level 55 Agility Etceteria Stepping Stone (Etceteria, 10300) [RuneLite shortcut MISCELLANIA_DOCK_STEPPING_STONE]
		EXTRA_OBJECTS.put("agility_level_70_fossil_island_hole", ids(31481, 31482)); // Use the Level 70 Agility Fossil Island Hole Shortcut (Fossil Island Museum Camp, 14907) [RuneLite shortcut FOSSIL_ISLAND_HARDWOOD]
		EXTRA_OBJECTS.put("complete_wilderness_agility", ids(23137, 23132, 23556, 23542, 23640)); // Use the Wilderness Agility Course (Frozen Waste Plateau, 11837) [RuneLite course Wilderness]
		EXTRA_OBJECTS.put("agility_level_10_rope_swing", ids(23568, 23569)); // Use the Level 10 Agility Moss Giant Island Rope Swing (Golden Peninsula, 10802) [RuneLite shortcut KARAMJA_MOSS_GIANT_SWING]
		EXTRA_OBJECTS.put("agility_level_21_underwall_tunnel", ids(16529, 16530)); // Use the Level 21 Agility Underwall Tunnel (Grand Exchange, 12598) [RuneLite shortcut GRAND_EXCHANGE_UNDERWALL_TUNNEL]
		EXTRA_OBJECTS.put("agility_level_71_gu'tanoth_wall_climb", ids(40355, 40356)); // Use the Level 71 Agility Gu'Tanoth Wall Climb (Gu'Tanoth, 10031) [RuneLite shortcut GU_TANOTH_CRUMBLING_WALL]
		EXTRA_OBJECTS.put("use_darkmeyer_long_rope_shortcut_63", ids(39541, 39542)); // Use the Level 63 Agility Darkmeyer Long Rope Shortcut (Hallowed Sepulchre, 14644) [RuneLite shortcut DARKMEYER_WALL]
		EXTRA_OBJECTS.put("agility_level_74_house_on_the_hill_zipline", ids(57667)); // Use the Level 74 Agility House on the Hill Zipline (House on the Hill, 14908) [RuneLite shortcut FOSSIL_ISLAND_ZIPLINE]
		EXTRA_OBJECTS.put("agility_level_53_musa_passage_grapple", ids(17074)); // Use the Level 53 Agility Musa Passage Grapple (Jogre Dungeon, 11312) [RuneLite shortcut KARAMJA_VOLCANO_GRAPPLE_SOUTH]
		EXTRA_OBJECTS.put("agility_level_57_rellekka_fence_jump", ids(544)); // Use the Level 57 Agility Rellekka Fence Jump (Keldagrim Cliffside, 10809) [RuneLite shortcut RELEKKA_EAST_FENCE]
		EXTRA_OBJECTS.put("agility_level_82_agility_stepping_stone_lava_maze", ids(14917)); // Use the Level 82 Agility Lava Maze Stepping Stone (Lava Maze, 12348) [RuneLite shortcut LAVA_MAZE_NORTH_JUMP]
		EXTRA_OBJECTS.put("agility_level_69_mausoleum_bridge_jump", ids(57715, 57716, 57717, 57718)); // Use the Level 69 Agility Mausoleum Bridge Jump (Mausoleum, 13879) [RuneLite shortcut FENKENSTRAIN_MAUSOLEUM_BRIDGE]
		EXTRA_OBJECTS.put("use_blood_altar_shortcut_74", ids(43755, 43756, 43757, 43758)); // Use the Level 74 Agility Blood Altar Cave Shortcut (Mort Myre Myreque Hideout, 13877) [RuneLite shortcut MEIYERDITCH_LAB_TUNNELS]
		EXTRA_OBJECTS.put("use_mos_leharmless_stepping_stone_60", ids(19042)); // Use the Level 60 Agility Mos Le'Harmless Stepping Stone (Mos Le'Harmless, 14638) [RuneLite shortcut MOS_LEHARMLESS_STEPPING_STONE]
		EXTRA_OBJECTS.put("agility_level_62_Necropolis_stepping_stone", ids(43990)); // Use the Level 62 Agility Necropolis Stepping Stone (Necropolis Fairy Ring, 13098) [RuneLite shortcut NECROPOLIS_STEPPING_STONE_NORTH]
		EXTRA_OBJECTS.put("agility_level_33_tunnel_nemus_retreat", ids(56989)); // Use the Level 33 Tunnel Shortcut at Nemus Retreat (Nemus Retreat, 5427) [RuneLite shortcut NEMUS_RETREAT_TUNNEL]
		EXTRA_OBJECTS.put("agility_level_36_stepping_stones_nemus_retreat", ids(56988)); // Use the Level 36 Stepping Stones Shortcut at Nemus Retreat (Nemus Retreat, 5427) [RuneLite shortcut NEMUS_RETREAT_STEPPING_STONES]
		EXTRA_OBJECTS.put("agility_level_41_rocks_nemus_retreat", ids(56994)); // Use the Level 41 Rocks Shortcut at Nemus Retreat (Nemus Retreat, 5427) [RuneLite shortcut AUBURNVALE_ROCK_SCRAMBLE]
		EXTRA_OBJECTS.put("agility_level_65_paterdomus_ornate_railing", ids(16552, 16998, 16999, 17000)); // Use the Level 65 Agility Paterdomus Temple Ornate Railing (Paterdomus Temple, 13622) [RuneLite shortcut MORYTANIA_TEMPLE]
		EXTRA_OBJECTS.put("agility_level_46_agility_deep_wilderness_dungeon_narrow_crevice", ids(19043)); // Use the Level 46 Agility Deep Wilderness Dungeon Narrow Crevice (Pirates' Hideout, 12093) [RuneLite shortcut DEEP_WILDERNESS_DUNGEON_CREVICE]
		EXTRA_OBJECTS.put("agility_level_71_pollnivneach_stepping_stone", ids(53241)); // Use the Level 71 Agility Pollnivneach Stepping Stone (Pollnivneach, 13358) [RuneLite shortcut POLLNIVNEACH_STEPPING_STONE]
		EXTRA_OBJECTS.put("complete_rellekka_roof", ids(14946, 14947, 14987, 14990, 14991, 14992, 14994)); // Use the Rellekka Rooftop Course (Rellekka, 10553) [RuneLite course Rellaka]
		EXTRA_OBJECTS.put("agility_level_48_fremennik_log_balance", ids(16540, 16541, 16542)); // Use the Level 48 Agility Fremennik Log Balance (Rellekka Forest, 10808) [RuneLite shortcut FREMENNIK_LOG_BALANCE]
		EXTRA_OBJECTS.put("revenant_cave_jump_shortcut", ids(31561)); // Use an Agility Jump Shortcut in the Revenant Caves (Revenant Caves, 12347)
		EXTRA_OBJECTS.put("agility_level_1_log_amja_river", ids(23644)); // Use the Level 1 Agility River Amja Log (River Amja, 11567) [RuneLite shortcut KARAMJA_GLIDER_LOG]
		EXTRA_OBJECTS.put("complete_seers'_roof", ids(14927, 14928, 14932, 14929, 14930, 14931)); // Use the Seers' Rooftop Course (Seers' Village, 10806) [RuneLite course Seers]
		EXTRA_OBJECTS.put("complete_advanced_shayzien_course", ids(42217, 42218, 42219, 42220, 42221)); // Use the Shayzien Advanced Agility Course (Shayzien Combat Ring, 6200) [RuneLite course Shayzien hard]
		EXTRA_OBJECTS.put("complete_basic_shayzien_course", ids(42213, 42214, 42215, 42216)); // Use the Shayzien Basic Agility Course (Shayzien Combat Ring, 6200) [RuneLite course Shayzien basic]
		EXTRA_OBJECTS.put("agility_level_32_shilo_village_stepping_stones", ids(16466)); // Use the Level 32 Agility Shilo Village Stepping Stones (Shilo Village, 11310) [RuneLite shortcut SHILO_VILLAGE_STEPPING_STONES]
		EXTRA_OBJECTS.put("agility_level_79_shilo_village_rock", ids(53238, 53240)); // Use the Level 79 Agility Shilo Village Rock Shortcut (Shilo Village, 11310) [RuneLite shortcut SHILO_VILLAGE_ROCKS]
		EXTRA_OBJECTS.put("agility_level_30_shilo_village_waterfall_stepping_stones", ids(23645, 23646, 23647)); // Use the Level 30 Agility Shilo Village Waterfall Stepping Stones (Shilo Village Entrance, 11566) [RuneLite shortcut SOUTHEAST_KARAMJA_STEPPING_STONES]
		EXTRA_OBJECTS.put("use_salve_river_stepping_stone_50", ids(13504)); // Use the Level 50 Agility Salve River Stepping Stone (Snail Path, 13619) [RuneLite shortcut MORYTANIA_STEPPING_STONE]
		EXTRA_OBJECTS.put("agility_crandor_level_84_rock_climb", ids(53234, 53236)); // Use the Level 84 Agility Crandor Rock Climb (South Crandor, 11314) [RuneLite shortcut CRANDOR_ROCK_CLIMB]
		EXTRA_OBJECTS.put("agility_level_64_agility_stepping_stone_stalker_den", ids(57277)); // Use the Level 64 Agility Stepping Stone Shortcut in the Stalker Den (Stalker Den, 5172) [RuneLite shortcut STALKER_DEN_STEEPING_STONE]
		EXTRA_OBJECTS.put("agility_level_70_pipe_squeeze", ids(16509)); // Use the Level 70 Agility Pipe Shortcut (Taverly Dungeon Entrance, 11573) [RuneLite shortcut TAVERLEY_DUNGEON_PIPE_BLUE_DRAGON]
		EXTRA_OBJECTS.put("agility_level_45_rock_climb_proudspire", ids(56983)); // Use the Level 45 Agility Rock Climb at The Proudspire (The Proudspire, 6194) [RuneLite shortcut PROUDSPIRE_LOWER_ROCKS]
		EXTRA_OBJECTS.put("agility_level_71_rock_climb_proudspire", ids(56978, 56980)); // Use the Level 71 Agility Rock Climb at The Proudspire (The Proudspire, 6194) [RuneLite shortcut PROUDSPIRE_UPPER_ROCKS]
		EXTRA_OBJECTS.put("agility_level_40_agility_log_balance_tlati_rainforest", ids(57593)); // Use the Level 40 Agility Log Balance Shortcut in the Tlati Rainforest (Tlati Rainforest North, 5169) [RuneLite shortcut TLATI_RAINFORST_BALANCE]
		EXTRA_OBJECTS.put("agility_level_72_gnome_stronghold_slayer_cave_tunnel", ids(30174, 30175)); // Use the Level 72 Agility Gnome Stronhold Slayer Cave Tunnel (Tree Gnome Stronghold Slayer Cave, 9525) [RuneLite shortcut STRONGHOLD_SLAYER_CAVE_TUNNEL]
		EXTRA_OBJECTS.put("complete_gnome_agility", ids(23134, 23559, 23560, 23135, 23138, 23139, 23145, 23557)); // Use the Gnome Agility Course (Tree Gnome Stronghold South East, 9781) [RuneLite course Gnome]
		EXTRA_OBJECTS.put("agility_level_73_troll_stronghold_wall_climb", ids(16464)); // Use the Level 73 Troll Stronghold wall-climb (Troll Stronghold, 11321) [RuneLite shortcut TROLL_STRONGHOLD_WALL_CLIMB]
		EXTRA_OBJECTS.put("agility_level_41_easy_cliffside_scramble", ids(16521)); // Use the Level 41 Trollheim Easy Cliffside Scramble (Trollheim, 11577) [RuneLite shortcut TROLLHEIM_EASY_CLIFF_SCRAMBLE]
		EXTRA_OBJECTS.put("agility_level_43_medium_cliffside_scramble", ids(16522)); // Use the Level 43 Trollheim Medium Cliffside Scramble (Trollheim, 11577) [RuneLite shortcut TROLLHEIM_MEDIUM_CLIFF_SCRAMBLE_SOUTHWEST]
		EXTRA_OBJECTS.put("agility_level_44_advanced_cliffside_scramble", ids(16523)); // Use the Level 44 Trollheim Advanced Cliffside Scramble (Trollheim, 11577) [RuneLite shortcut TROLLHEIM_ADVANCED_CLIFF_SCRAMBLE]
		EXTRA_OBJECTS.put("agility_level_47_hard_cliffside_scramble", ids(16524)); // Use the Level 47 Trollheim Hard Cliffside Scramble (Trollheim, 11577) [RuneLite shortcut TROLLHEIM_HARD_CLIFF_SCRAMBLE]
		EXTRA_OBJECTS.put("agility_level_64_trollheim_wilderness_route", ids(16545)); // Use the Level 64 Trollheim Wilderness Route (Trollheim, 11577) [RuneLite shortcut TROLLHEIM_WILDERNESS_ROCKS_WEST]
		EXTRA_OBJECTS.put("agility_level_74_agility_stepping_stone_lava_dragon", ids(14918)); // Use the Level 74 Agility Lava Dragon Stepping Stone (Vet'ion's Rest, 12859) [RuneLite shortcut LAVA_DRAGON_ISLE_JUMP]
		EXTRA_OBJECTS.put("agility_level_64_volcanic_mine_rope", ids(30916, 30917)); // Use the Level 64 Agility Volcanic Mine Rope (Volcanic Mine, 15163) [RuneLite shortcut FOSSIL_ISLAND_VOLCANO]
		EXTRA_OBJECTS.put("agility_level_40_river_hos_stepping_stones", ids(29729, 29730)); // Use the Level 40 Agility River Hos Stepping Stone (Watson, 6455) [RuneLite shortcut KOUREND_LAKE_JUMP]
		EXTRA_OBJECTS.put("agility_level_10_corsair_cove_rocks", ids(31757)); // Use the Level 10 Agility Corsair Cove Rocks (West Corsair Cove, 10028) [RuneLite shortcut CORSAIR_COVE_ROCKS]
		EXTRA_OBJECTS.put("use_werewolf_agility_zipline", ids(11643, 11638, 11639, 11640, 11657, 1747, 11644, 11645, 11646)); // Use the Werewolf Agility Course Zip Line (West Haunted Woods, 14134) [RuneLite course Werewolf]
		EXTRA_OBJECTS.put("agility_level_39_west_yanille_wall_grapple_shortcut", ids(17047, 17048)); // Use the Level 39 Agility West Yanille Wall Grapple Shortcut (West Yanille, 10032) [RuneLite shortcut YANILLE_WALL_GRAPPLE]

	}

	private static Map<Skill, Integer> levels(Object... skillsAndLevels)
	{
		Map<Skill, Integer> map = new EnumMap<>(Skill.class);
		for (int i = 0; i + 1 < skillsAndLevels.length; i += 2)
		{
			map.put((Skill) skillsAndLevels[i], (Integer) skillsAndLevels[i + 1]);
		}
		return map;
	}

	/**
	 * Every level a task needs: its own (category + level, "_Set" categories included)
	 * plus any listed in REQUIREMENTS (gear needing several skills, multi-skill tasks the
	 * data only half-holds, tasks with no level). The highest wins where both name a skill.
	 */
	static Map<Skill, Integer> requirements(NuzlockeTask task)
	{
		Map<Skill, Integer> needs = new EnumMap<>(Skill.class);
		Skill own = categorySkill(task.getCategory());
		if (own != null && task.getLevelRequirement() > 1)
		{
			needs.put(own, task.getLevelRequirement());
		}
		Map<Skill, Integer> extra = task.getTaskId() == null ? null : REQUIREMENTS.get(task.getTaskId());
		if (extra != null)
		{
			for (Map.Entry<Skill, Integer> need : extra.entrySet())
			{
				needs.merge(need.getKey(), need.getValue(), Math::max);
			}
		}
		return needs;
	}

	/**
	 * Every requirement the player is missing, like "20 Defence, 20 Ranged", or null if
	 * they have them all.
	 */
	static String missingRequirement(Client client, NuzlockeTask task)
	{
		StringBuilder missing = new StringBuilder();
		for (Map.Entry<Skill, Integer> need : requirements(task).entrySet())
		{
			if (client.getRealSkillLevel(need.getKey()) < need.getValue())
			{
				if (missing.length() > 0)
				{
					missing.append(", ");
				}
				missing.append(need.getValue()).append(' ').append(need.getKey().getName());
			}
		}
		return missing.length() == 0 ? null : missing.toString();
	}

	/**
	 * The skill a task category names: "Mining", "Runecrafting" (Runecraft), and "_Set"
	 * categories like "Herblore_Set" (Herblore). Null for Combat, Quest, Progression, etc.
	 */
	static Skill categorySkill(String category)
	{
		if (category == null)
		{
			return null;
		}
		String name = category.trim().toUpperCase();
		if (name.endsWith("_SET"))
		{
			name = name.substring(0, name.length() - 4);
		}
		if (name.equals("RUNECRAFTING"))
		{
			name = "RUNECRAFT";
		}
		try
		{
			return Skill.valueOf(name);
		}
		catch (IllegalArgumentException e)
		{
			return null;
		}
	}

	private static Set<Integer> ids(int... ids)
	{
		Set<Integer> set = new HashSet<>();
		for (int id : ids)
		{
			set.add(id);
		}
		return set;
	}

	/** Extra object ids for this task (empty if none). */
	static Set<Integer> objectIds(NuzlockeTask task)
	{
		return task.getTaskId() == null ? Collections.emptySet()
			: EXTRA_OBJECTS.getOrDefault(task.getTaskId(), Collections.emptySet());
	}

	/** Extra NPC ids for this task (empty if none). */
	static Set<Integer> npcIds(NuzlockeTask task)
	{
		return task.getTaskId() == null ? Collections.emptySet()
			: EXTRA_NPCS.getOrDefault(task.getTaskId(), Collections.emptySet());
	}

	/** Object name rules for this task: patches, stalls, STASH units. */
	static List<Rule> objectRules(NuzlockeTask task)
	{
		String type = type(task);
		String name = lower(task.getName());

		if (type.equals("FARMING") && name.startsWith("plant"))
		{
			String crop = name + " " + firstItem(task);
			for (Map.Entry<String, String> entry : PATCHES.entrySet())
			{
				if (crop.contains(entry.getKey()))
				{
					String patch = entry.getValue();
					return Collections.singletonList(new Rule("extra:patch:" + patch, n -> n.startsWith(patch)));
				}
			}
			return Collections.emptyList();
		}

		if (type.equals("THIEVING") && name.startsWith("steal from a") && name.endsWith("stall"))
		{
			String stall = letters(name.replaceFirst("^steal from an? ", ""));
			return Collections.singletonList(new Rule("extra:stall:" + stall,
				n -> letters(n).equals(stall), "steal-from", "steal from"));
		}

		// Silver crafting is done at a furnace, like jewellery (same rule as the plugin's).
		if (type.equals("CRAFTING") && containsAny(name, "tiara", "unstrung symbol", "unstrung emblem", "silver sickle"))
		{
			return Collections.singletonList(new Rule("station:smelt", n -> true, "smelt"));
		}

		if (type.equals("CONSTRUCTION") && name.contains("stash unit"))
		{
			for (String tier : Arrays.asList("beginner", "easy", "medium", "hard", "elite", "master"))
			{
				if (name.contains(tier))
				{
					return Collections.singletonList(new Rule("extra:stash:" + tier,
						n -> n.contains("stash unit") && n.contains(tier)));
				}
			}
		}
		return Collections.emptyList();
	}

	/**
	 * Whether right-clicking this inventory item should list the task. Tools stand in
	 * for skills done from the inventory; bones and ashes for their bury/scatter task.
	 * {@code item} is the item's name in lowercase.
	 */
	static boolean matchesItem(NuzlockeTask task, String item)
	{
		String type = type(task);
		String name = lower(task.getName());
		switch (item)
		{
			case "knife":
				return type.equals("FLETCHING") || (type.equals("CRAFTING") && name.contains("dramen staff"));
			case "tinderbox":
				return type.equals("FIREMAKING");
			case "pestle and mortar":
				return type.equals("HERBLORE");
			case "needle":
				return type.equals("CRAFTING") && containsAny(name, "leather", "hide", "snakeskin", "coif",
					"xerician", "meat pouch");
			case "chisel":
				return type.equals("CRAFTING") && (name.matches("^cut an? .*") || name.contains("snelm"));
			case "glassblowing pipe":
				return type.equals("CRAFTING") && containsAny(name, "vial", "orb", "lantern", "lamp", "lens",
					"fishbowl", "beer glass");
			case "hammer":
				return type.equals("CRAFTING") && name.contains("broodoo");
			default:
				// Bones and ashes: the bury/scatter task that uses exactly this item.
				return type.equals("PRAYER") && !item.isEmpty() && item.equals(firstItem(task));
		}
	}

	private static boolean containsAny(String text, String... words)
	{
		for (String word : words)
		{
			if (text.contains(word))
			{
				return true;
			}
		}
		return false;
	}

	/** NPC name rule for this task: pickpocketing, hunter creatures, kills with no ids. */
	static Rule npcRule(NuzlockeTask task)
	{
		String type = type(task);
		String name = lower(task.getName());

		if (type.equals("THIEVING") && name.matches("^pickpocket an? .*"))
		{
			String target = npcName(name.replaceFirst("^pickpocket an? ", ""));
			return new Rule("extra:pickpocket:" + target, n -> letters(n).equals(target), "pickpocket");
		}
		if (type.equals("HUNTER") && name.matches("^catch an? .*"))
		{
			String target = npcName(name.replaceFirst("^catch an? ", "").replaceFirst(" in a butterfly jar$", ""));
			return new Rule("extra:hunt:" + target, n -> letters(n).equals(target));
		}
		String named = task.getTaskId() == null ? null : NAMED_NPCS.get(task.getTaskId());
		if (named != null)
		{
			String target = letters(named);
			return new Rule("extra:named:" + target, n -> letters(n).equals(target));
		}

		// Imp Catcher beads drop from imps (any spawn, so matched by name).
		if (type.equals("OBTAIN") && name.matches("^obtain an? (black|yellow|white|red) bead$"))
		{
			return new Rule("extra:kill:imp", n -> letters(n).equals("imp"), "attack");
		}
		if ((type.equals("NPC_KILL") || type.equals("SLAYER")) && name.matches("^defeat an? .*"))
		{
			String target = npcName(name.replaceFirst("^defeat an? ", "").replaceFirst(" on task$", ""));
			return new Rule("extra:kill:" + target, n -> letters(n).equals(target), "attack");
		}
		return null;
	}

	/** The in-game NPC name (letters only) a task's wording refers to. */
	private static String npcName(String wording)
	{
		String key = letters(wording);
		return NAME_ALIASES.getOrDefault(key, key);
	}

	private static String type(NuzlockeTask task)
	{
		return task.getCompletionType() == null ? "" : task.getCompletionType().toUpperCase();
	}

	private static String lower(String text)
	{
		return text == null ? "" : text.toLowerCase().trim();
	}

	/** Lowercase letters only: "Baker's Stall" and "baker's stall" both become "bakersstall". */
	private static String letters(String text)
	{
		return text == null ? "" : text.toLowerCase().replaceAll("[^a-z]", "");
	}

	private static String firstItem(NuzlockeTask task)
	{
		if (task.getRequiredItems() == null || task.getRequiredItems().isEmpty()
			|| task.getRequiredItems().get(0).getItem() == null)
		{
			return "";
		}
		return task.getRequiredItems().get(0).getItem().toLowerCase();
	}
}
