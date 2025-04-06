package com.fantasticsource.hardasarock;

import com.fantasticsource.mctools.blocks.RegistryRegexBlockFilter;
import com.fantasticsource.tools.Tools;
import net.minecraft.block.Block;
import net.minecraft.block.BlockButton;
import net.minecraft.block.BlockOre;
import net.minecraft.block.BlockRedstoneOre;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

public class MiningData
{
    public static final Random RANDOM = new Random();
    public static final ItemStack TEST_PICK = new ItemStack(Items.DIAMOND_PICKAXE);
    public static LinkedHashMap<ArrayList<RegistryRegexBlockFilter>, Integer> monolithicGroups = new LinkedHashMap<>();


    public static void update()
    {
        monolithicGroups.clear();

        //Iterate in reverse order to make it easier to correctly apply general rules at top of config and more specific rules at bottom
        RegistryRegexBlockFilter filter;
        for (int i = MiningConfig.monolithicBlockGroups.length - 1; i >= 0; i--)
        {
            String line = MiningConfig.monolithicBlockGroups[i].trim();
            int mode = 0;
            if (line.startsWith("~ "))
            {
                mode = 1;
                line = line.substring(2);
            }
            else if (line.startsWith("! "))
            {
                mode = 2;
                line = line.substring(2);
            }

            ArrayList<RegistryRegexBlockFilter> group = new ArrayList<>();

            String[] tokens = Tools.fixedSplit(line, ",");
            for (String token : tokens)
            {
                filter = RegistryRegexBlockFilter.getInstance(token.trim());
                if (filter != null) group.add(filter);
            }

            if (group.size() > 0) monolithicGroups.put(group, mode);
        }
    }


    public static boolean isPickaxeBlock(IBlockState block)
    {
        //Special case for buttons (for some reason stone buttons were indestructible)
        if (block.getBlock() instanceof BlockButton) return false;

        //Also allow gathering of PVJ clutter by hand
        if ("vibrantjourneys.blocks.BlockGroundCover".equals(block.getBlock().getClass().getName())) return false;

        //Weird way of checking, but apparently a lot of modded blocks that should have a harvest tool set don't, so need a more roundabout way
        return TEST_PICK.getDestroySpeed(block) > 1;
    }

    public static boolean isOre(IBlockState block)
    {
        Block blockType = block.getBlock();
        if (blockType instanceof BlockOre || blockType instanceof BlockRedstoneOre) return true;

        //Another weird, roundabout way of detection due to other mods not extending BlockOre
        ItemStack drop = new ItemStack(blockType.getItemDropped(block, RANDOM, 0), 1, blockType.damageDropped(block));
        if (!(drop.getItem() instanceof ItemBlock)) return true;
        ItemStack smeltingResult = FurnaceRecipes.instance().getSmeltingResult(drop);
        return !smeltingResult.isEmpty() && !(smeltingResult.getItem() instanceof ItemBlock);
    }


    public static boolean areMonolithic(IBlockState block, IBlockState adjacent)
    {
        for (Map.Entry<ArrayList<RegistryRegexBlockFilter>, Integer> group : monolithicGroups.entrySet())
        {
            boolean stateFound = false, adjacentFound = false;

            for (RegistryRegexBlockFilter filter : group.getKey())
            {
                if (filter.matches(block)) stateFound = true;
                if (filter.matches(adjacent)) adjacentFound = true;
                if (stateFound && adjacentFound)
                {
                    //Both blocks are in the group
                    switch (group.getValue())
                    {
                        case 0:
                            return true;

                        case 1:
                            Block blockType = block.getBlock();
                            return blockType == adjacent.getBlock() && blockType.getMetaFromState(block) == blockType.getMetaFromState(adjacent);

                        case 2:
                            return false;
                    }
                }
            }

            if (stateFound || adjacentFound)
            {
                //Only one block is in the group
                return false;
            }
        }


        //Default behavior
        Block blockType = block.getBlock();
        if (blockType != adjacent.getBlock()) return false;

        int meta = blockType.getMetaFromState(block);
        if (meta != blockType.getMetaFromState(adjacent)) return false;

        if (blockType == Blocks.STONE) return meta == 0 || meta % 2 == 1;

        return !isOre(block) && !block.getBlock().getRegistryName().getResourcePath().contains("cobble");
    }
}
