package com.fantasticsource.hardasarock;

import net.minecraftforge.common.config.Config;

import static com.fantasticsource.hardasarock.HardAsARock.MODID;

@Config(modid = MODID)
public class MiningConfig
{
    //Block mining levels (harvest level overrides; -infinity for setting as "not a pickaxe block" and infinity for setting as infinite harvest level; very likely unbreakable)
    //Ore blocks

    @Config.Name("050 Monolithic Block Groups")
    @Config.LangKey(MODID + ".config.monolithicGroups")
    @Config.Comment(
            {
                    "FILLSCREEN Which groupings of blocks should be harder to mine when connected to each other",
                    "",
                    "Normal layout is domain:name:meta, domain:name:meta, etc",
                    "Domain, name, and meta can be regex: .*:.*:.* will match everything, .* will match all vanilla with 0 meta",
                    "",
                    "Starting the line with ~ (and then a space) makes things connect only to themselves",
                    "",
                    "Starting the line with ! (and then a space) makes things not connect to anything",
                    "",
                    "Make all \"stone\" oredict blocks connect to each other:",
                    "ore:stone",
                    "",
                    "Make unpolished stone/granite/diorite/andesite/glowstone connect ONLY TO THEMSELVES, NOT EACH OTHER:",
                    "~ stone:0, stone:1, stone:3, stone:5, glowstone",
                    "",
                    "Make polished granite/diorite/andesite NOT CONNECT TO ANYTHING:",
                    "! stone:2, stone:4, stone:6",
                    " ",
            })
    public static String[] monolithicBlockGroups = new String[]{};
}
