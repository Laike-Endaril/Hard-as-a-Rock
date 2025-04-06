package com.fantasticsource.hardasarock;

import com.fantasticsource.mctools.BlockProtection;
import com.fantasticsource.mctools.ImprovedRayTracing;
import com.fantasticsource.mctools.MCTools;
import com.fantasticsource.tools.Tools;
import net.minecraft.block.BlockRailBase;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.OreIngredient;
import net.minecraftforge.registries.ForgeRegistry;

import java.util.ArrayList;
import java.util.HashMap;

@Mod(modid = HardAsARock.MODID, name = HardAsARock.NAME, version = HardAsARock.VERSION, dependencies = "required-after:fantasticlib@[1.12.2.057,)")
public class HardAsARock
{
    public static final String MODID = "hardasarock";
    public static final String NAME = "Hard as a Rock";
    public static final String VERSION = "1.12.2.001";

    public static final HashMap<EntityPlayer, Double> LAST_DIGGING_TIMES = new HashMap<>();


    @Mod.EventHandler
    public static void preInit(FMLPreInitializationEvent event)
    {
        MinecraftForge.EVENT_BUS.register(HardAsARock.class);


        //Make the stone pickaxe recipe use solid stone instead of cobble
        ForgeRegistry<IRecipe> recipeRegistry = (ForgeRegistry<IRecipe>) ForgeRegistries.RECIPES;
        ShapedRecipes stonePickaxeRecipe = (ShapedRecipes) recipeRegistry.getValue(new ResourceLocation("minecraft:stone_pickaxe"));
        ItemStack cobbleStack = new ItemStack(Blocks.COBBLESTONE);

        OreIngredient ingredientStone = new OreIngredient("stone");
        stonePickaxeRecipe.getIngredients().replaceAll(ingredient -> ingredient.apply(cobbleStack) ? ingredientStone : ingredient);


        //Make flint count as solid stone
        OreDictionary.registerOre("stone", Items.FLINT);

        //Make bone count as a stick
        OreDictionary.registerOre("stickWood", Items.BONE);
    }

    @SubscribeEvent
    public static void saveConfig(ConfigChangedEvent.OnConfigChangedEvent event)
    {
        if (event.getModID().equals(MODID)) ConfigManager.sync(MODID, Config.Type.INSTANCE);
    }


    @Mod.EventHandler
    public static void serverStarting(FMLServerStartingEvent event)
    {
        MiningData.update();
    }

    @SubscribeEvent
    public static void postConfigChanged(ConfigChangedEvent.PostConfigChangedEvent event)
    {
        MiningData.update();
    }


    @SubscribeEvent
    public static void alterBlock(BlockProtection.AlterBlockEvent event)
    {
        if (event.entityAltering instanceof EntityPlayer)
        {
            World world = event.world;
            IBlockState block = world.getBlockState(event.blockPos);
            if (MiningData.isPickaxeBlock(block))
            {
                if (event.originalEvent instanceof PlayerEvent.BreakSpeed || event.originalEvent instanceof PlayerInteractEvent.LeftClickBlock)
                {
                    EntityPlayer player = (EntityPlayer) event.entityAltering;
                    ItemStack tool = player.getHeldItemMainhand();
                    int toolLevel = getAdjustedHarvestLevel(tool, player, block);


                    //If we try to punch stone, cancel
                    if (tool.isEmpty())
                    {
                        event.setCanceled(true);
                        return;
                    }


                    //This is an edge case and likely means the vanilla raytrace bugged out to cause a mismatched result (since the vanilla raytrace has confirmed bugs and mine doesn't)
                    EnumFacing dugFace = ImprovedRayTracing.rayTraceBlocks(player, MCTools.getAttribute(player, EntityPlayer.REACH_DISTANCE), true).sideHit;
                    if (dugFace == null)
                    {
                        event.setCanceled(true);
                        return;
                    }


                    //Calculate how hard it is to dig this block
                    double digDifficulty = getAdjustedHarvestLevel(block);
                    if ((!tool.canHarvestBlock(block) && !(block.getBlock() instanceof BlockRailBase)) || digDifficulty > toolLevel)
                    {
                        //If we can't dig it, cancel
                        event.setCanceled(true);
                        return;
                    }


                    //Adjust dig difficulty for difference between tool level and block level
                    //After this line, the value is always between 1 and infinitesimal
                    digDifficulty = 1.0 / (toolLevel + 1 - digDifficulty);


                    //Adjust dig difficulty based on connections (how "thick" the current rock is, simplified)
                    int connections = 0;
                    BlockPos pos = event.blockPos, adjacent;
                    ArrayList<BlockPos> obstructions = new ArrayList<>();
                    IBlockState adjacentBlock;
                    for (EnumFacing facing : EnumFacing.values())
                    {
                        adjacent = pos.offset(facing);
                        adjacentBlock = world.getBlockState(adjacent);
                        if (block.isSideSolid(world, pos, facing) && adjacentBlock.isSideSolid(world, adjacent, facing.getOpposite()) && MiningData.areMonolithic(block, adjacentBlock))
                        {
                            connections++;
                            obstructions.add(adjacent);
                        }
                    }
                    if (world.isRemote && connections > 1)
                    {
                        //On client only, make particles on obstructing blocks
                        for (BlockPos obstruction : obstructions) createObstructionParticles(world, obstruction);
                    }
                    digDifficulty *= Tools.max(1, connections); //digDifficulty now ranges from 5 (5 connections, digging exposed face with minimum tool level) to infinitesimal


                    //Endgame adjustment; make sure top level tool can mine it's own level of block with 3 connections
                    //This ensures any continuous stone can be mined starting from a corner, if there is one (ie. player-placed and most worldgen)
                    int maxToolLevel = 0;
                    for (Item.ToolMaterial toolMaterial : Item.ToolMaterial.values())
                    {
                        int materialLevel = getAdjustedHarvestLevel(toolMaterial);
                        if (materialLevel > maxToolLevel) maxToolLevel = materialLevel;
                    }
                    if (toolLevel == maxToolLevel) digDifficulty *= 0.33;


                    if (digDifficulty > 1)
                    {
                        //If we can't dig it, cancel
                        event.setCanceled(true);
                        return;
                    }


                    if (event.originalEvent instanceof PlayerEvent.BreakSpeed)
                    {
                        //Track digging time to use for pick durability calcs
                        LAST_DIGGING_TIMES.put(player, digDifficulty);


                        //Initialize digging speed based on dig difficulty
                        double speedMultiplier = 1.0 / digDifficulty;

                        //Reverse calcs in ForgeHooks.blockStrength
                        double hardness = block.getBlockHardness(world, pos);
                        if (hardness <= 0) speedMultiplier = Double.POSITIVE_INFINITY;
                        else
                        {
                            if (ForgeHooks.canHarvestBlock(block.getBlock(), player, world, pos)) speedMultiplier *= hardness * 30;
                            else speedMultiplier *= hardness * 100;
                        }


                        //Make non-connected blocks mine faster
                        if (connections == 0) speedMultiplier *= 2;


                        //Re-apply any adjustments made from normal mining speed done by vanilla or mods (enchants, potions, etc) up until this point (further adjustments from mods can be done afterwards)
                        speedMultiplier *= ((PlayerEvent.BreakSpeed) event.originalEvent).getNewSpeed() / player.inventory.getDestroySpeed(block);


                        //Final global digging speed adjustment
                        speedMultiplier *= 0.01;


                        //Set new dig speed; ignore normal digging times
                        ((PlayerEvent.BreakSpeed) event.originalEvent).setNewSpeed((float) speedMultiplier);
                    }
                }
            }
        }
    }


    @SubscribeEvent
    public static void onHarvest(BlockEvent.HarvestDropsEvent event)
    {
        IBlockState brokenBlock = event.getState();
        EntityPlayer player = event.getHarvester();
        if (player != null && MiningData.isPickaxeBlock(brokenBlock))
        {
            //Deal additional damage to tool based on how long it took to chop
            ItemStack tool = player.getHeldItemMainhand();
            if (tool.isItemStackDamageable()) tool.damageItem((int) Tools.max(0, LAST_DIGGING_TIMES.getOrDefault(player, 1.0)), player);
        }
    }


    public static int getAdjustedHarvestLevel(IBlockState block)
    {
        //Lowest valid value is 1
        return Tools.max(0, block.getBlock().getHarvestLevel(block)) + 1;
    }

    public static int getAdjustedHarvestLevel(Item.ToolMaterial material)
    {
        return material.getHarvestLevel() + 1;
    }

    public static int getAdjustedHarvestLevel(ItemStack stack, EntityPlayer player, IBlockState block)
    {
        //Lowest valid value is 1
        return Tools.max(0, stack.getItem().getHarvestLevel(stack, "pickaxe", player, block)) + 1;
    }


    public static void createObstructionParticles(World world, BlockPos blockPos)
    {
        for (EnumFacing facing : EnumFacing.values())
        {
            if (!world.getBlockState(blockPos.offset(facing)).isFullBlock()) Minecraft.getMinecraft().effectRenderer.addBlockHitEffects(blockPos, facing);
        }
    }
}
