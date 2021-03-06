package com.programmerdan.minecraft.simpleadminhacks.hacks;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.SkeletonHorse;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import com.programmerdan.minecraft.simpleadminhacks.SimpleAdminHacks;
import com.programmerdan.minecraft.simpleadminhacks.SimpleHack;
import com.programmerdan.minecraft.simpleadminhacks.configs.GameTuningConfig;
import com.programmerdan.minecraft.simpleadminhacks.util.TeleportUtil;

import net.md_5.bungee.api.ChatColor;

/**
 * This is a grab-bag class to hold any _tuning_ related configurations that impact the
 * game, server-wide.
 *
 * It's part of a series of focused hacks.
 *
 * {@link GameFixes} is focused on things that are broken or don't work, and attempts to fix them.
 * {@link GameFeatures} focuses on enabling and disabling features, like elytra, various potion states.
 * {@link GameTuning} neither fixes nor disables, but rather adjusts and reconfigures.
 *
 * Currently you can control the following:
 * - BlockEntity limits per chunk
 * - Setting bed during the day instead of just at night
 */
public class GameTuning extends SimpleHack<GameTuningConfig> implements Listener {
	public static final String NAME = "GameTuning";
	
	private Random rng = new Random();

	public GameTuning(SimpleAdminHacks plugin, GameTuningConfig config) {
		super(plugin, config);
	}

	@Override
	public void registerListeners() {
		if (config != null && config.isEnabled()) {
			plugin().log("Registering GameTuning listeners");
			plugin().registerListener(this);
		}
	}

	@Override
	public void unregisterListeners() {
		// Bukkit does this for us.
	}

	@Override
	public void registerCommands() {
	}

	@Override
	public void unregisterCommands() {
	}

	@Override
	public void dataBootstrap() {
	}

	@Override
	public void dataCleanup() {
		// NO-OP
	}

	@Override
	public String status() {
		StringBuilder genStatus = new StringBuilder();
		genStatus.append("GameTuning is ");
		if (config != null && config.isEnabled()) {
			genStatus.append("active\n");
			if (config.areChunkLimitsEnabled()) {
				genStatus.append("  Chunk Limits are enabled\n");
			} else {
				genStatus.append("  Chunk Limits are disabled\n");
			}
			if (config.areDaytimeBedsEnabled()) {
				genStatus.append("  Daytime Beds are enabled\n");
			} else {
				genStatus.append("  Daytime Beds are disabled\n");
			}

			genStatus.append("  One To One Nether is ");
			if (config.isOneToOneNether()) {
				genStatus.append("enabled\n");
			} else {
				genStatus.append("disabled\n");
			}
			genStatus.append("  Stop trap horses is ");
			if (config.stopTrapHorses()) {
				genStatus.append("enabled\n");
			} else {
				genStatus.append("disabled\n");
			}
			// more?
		} else {
			genStatus.append("inactive");
		}
		return genStatus.toString();
	}

	public static GameTuningConfig generate(SimpleAdminHacks plugin, ConfigurationSection config) {
		return new GameTuningConfig(plugin, config);
	}

	/* From here on, the actual meat of the hack. Above is basically boilerplate for micro-plugins.*/

	/**
	 * Many thanks to BlackXNT for his work on this event in Humbug, which I have largely copied and expanded.
	 *
	 * This tracks block placements, and if a limit is configured and the block is a TileEntity w/ state,
	 * will reject the placement if otherwise it would exceed limits for the Chunk.
	 *
	 * @param event the Placement event.
	 */
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void chunkLimits(BlockPlaceEvent event) {
		if (!config.isEnabled() || !config.areChunkLimitsEnabled()) return;
		try {
			Player player = event.getPlayer();
			if (player == null) return;

			Block block = event.getBlock();
			if (block == null) return;

			if (!config.applyChunkLimits(player.getUniqueId())) return;

			Material mat = block.getType();
			if (mat == null) return;

			Integer limit = config.getChunkLimit(mat);
			if (limit == null) return;

			if (block.getChunk().getTileEntities() == null) return;

			int current = 0;
			for (BlockState state : block.getChunk().getTileEntities()) {
				if (state != null && mat.equals(state.getType())) {
					if (++current > limit) {
						event.setCancelled(true);
						player.sendMessage(config.getChunkLimitsExceededMessage());
						return;
					}
				}
			}
		} catch (Exception e) {
			plugin().log(Level.WARNING, "Failed to measure chunk limit", e);
		}
	}

	// If any limit at all, cancel the piston event.
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void chunkLimitsExploitExtend(BlockPistonExtendEvent event) {
		if (!config.isEnabled() || !config.areChunkLimitsEnabled()) return;
		List<Block> blocks = event.getBlocks();
		if (blocks != null && blocks.size() > 0) {
			for (Block b : blocks) {
				if (b != null && b.getType() != null && config.getChunkLimit(b.getType()) != null) {
					event.setCancelled(true);
					return; // TODO send message to nearby player warning of reason for stopping.
				}
			}
		}
	}

	// Yes, this is identical ...
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void chunkLimitsExploitRetract(BlockPistonRetractEvent event) {
		if (!config.isEnabled() || !config.areChunkLimitsEnabled()) return;
		List<Block> blocks = event.getBlocks();
		if (blocks != null && blocks.size() > 0) {
			for (Block b : blocks) {
				if (b != null && b.getType() != null && config.getChunkLimit(b.getType()) != null) {
					event.setCancelled(true);
					return; // TODO send message to nearby player warning of reason for stopping.
				}
			}
		}
	}

	@SuppressWarnings("deprecation")
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void bedRClickToSetSpawn(PlayerInteractEvent event) {
		if (!config.isEnabled() || !config.areDaytimeBedsEnabled() || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock().getType() != Material.BED_BLOCK) {
			return;
		}

		if (event.getPlayer() == null) return;
		if (event.getClickedBlock() == null) return;

		// Let plugins that already watch for and cancel this event have a turn, like ExilePearl
		PlayerBedEnterEvent pbee = new PlayerBedEnterEvent(event.getPlayer(), event.getClickedBlock());
		Bukkit.getServer().getPluginManager().callEvent(pbee);
		if (pbee.isCancelled()) {
			return;
		}

		event.getPlayer().setBedSpawnLocation(event.getClickedBlock().getLocation(), false);
		Location loc = event.getClickedBlock().getLocation();
		event.getPlayer().sendTitle("", config.getDaytimeBedSpawnSetMessage());
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPortalTravel(EntityPortalEvent event) {
		if (config.isEnabled() && config.isOneToOneNether()) {
			Location newLoc = event.getFrom();
			newLoc.setWorld(event.getTo().getWorld());
			event.setTo(newLoc);
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerPortalTravel(PlayerPortalEvent event) {
		if(config.isEnabled() && event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
			if(!config.allowNetherTravel()) {
				event.setCancelled(true);
			} else if (config.isOneToOneNether()) {
				Location newLoc = event.getFrom();
				newLoc.setWorld(event.getTo().getWorld());
				event.setTo(newLoc);
			}
		}
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onPortalCreate(PortalCreateEvent event) {
		if (config.isEnabled() && !config.isReturnNetherPortal()) {
			if (event.getReason() == PortalCreateEvent.CreateReason.FIRE && event.getWorld().getName().equals("world_nether")) {
				event.setCancelled(true);
			}
		}
	}

	//Trying to stop dupe bugs via minecart inventories
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onEntityRightClick(PlayerInteractEntityEvent event) {
		if (!config.isEnabled()) return;
		if (!config.isChestedMinecartInventories() || !config.isHopperMinecartInventories()) {
			Entity target = event.getRightClicked();

			if (target.getType().equals(EntityType.MINECART_CHEST) && !config.isChestedMinecartInventories()) {
				event.setCancelled(true);
			}

			if (target.getType().equals(EntityType.MINECART_HOPPER) && !config.isHopperMinecartInventories()) {
				event.setCancelled(true);
			}
		}
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onInventoryMoveItemEvent(InventoryMoveItemEvent event) {
		if (!config.isEnabled()) return;
		if (!config.isChestedMinecartInventories() || !config.isHopperMinecartInventories()) {
			InventoryHolder holder = event.getDestination().getHolder();
			if (holder instanceof StorageMinecart && !config.isChestedMinecartInventories()) {
				event.setCancelled(true);
			}

			if (holder instanceof HopperMinecart && !config.isHopperMinecartInventories()) {
				event.setCancelled(true);
			}

		}
	}
	
	@EventHandler
	public void onEntityTarget(EntityTargetEvent event) {
		if(config.isEnabled() && config.stopTrapHorses()) {
			Entity entity = event.getEntity();
			if(entity instanceof Skeleton && entity.isInsideVehicle() && entity.getVehicle() instanceof SkeletonHorse) {
				if(config.killTrapHorses()) {
					entity.getVehicle().remove();
				}
				entity.remove();
			}
		}
	}

	@EventHandler
	public void onPlayerInteract(PlayerInteractEvent event) {
		if(config.isEnabled() && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
			boolean cancel = !config.isEnderChestInventories() && event.getClickedBlock().getType() == Material.ENDER_CHEST;
			cancel = cancel || (!config.canChangeSpawnerType() && event.getClickedBlock().getType() == Material.MOB_SPAWNER
					&& event.getItem() != null && event.getItem().getType() == Material.MONSTER_EGG);
			event.setCancelled(cancel);
		}
	}
	
	@EventHandler
	public void onBlockPlace(BlockPlaceEvent event) {
		if(config.isEnabled() && !config.canPlace(event.getBlock().getType())) {
			event.setCancelled(true);
			event.getPlayer().sendMessage(ChatColor.RED + "You're not allowed to place that!");
		}
	}
	
	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
		if(config.isEnabled() && !config.allowVillagerTrading()) {
			Entity npc = event.getRightClicked();
			if(npc != null && npc.getType() == EntityType.VILLAGER) {
				event.setCancelled(true);
			}
		}
	}
	
	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void onEntityChangeBLock(EntityChangeBlockEvent event) {
		if(config.isEnabled() &&
				(!config.isEnderGrief() && event.getEntityType() == EntityType.ENDERMAN) ||
				(!config.isWitherGrief() && event.getEntityType() == EntityType.WITHER)) {
			event.setCancelled(true);
		}
	}
	
	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void spawnMoreHellMonsters(CreatureSpawnEvent event) {
		final Location loc = event.getLocation();
		final World world = loc.getWorld();
		Block block = loc.getBlock();
		boolean portal = block.getType() == Material.PORTAL || block.getType() == Material.OBSIDIAN;
		if(portal) {
			if(rng.nextInt(1000000) >= config.getPigPortalSpawnMultiplier()) {
				event.setCancelled(true);
				return;
			}
		}
		if(event.getEntityType() == EntityType.PIG_ZOMBIE) {
			int wither = portal ? config.getExtraWitherPortalSpawnRate() : config.getExtraWitherSpawnRate();
			int ghast = portal ? config.getExtraGhastPortalSpawnRate() : config.getExtraGhastSpawnRate();
			int magma = portal ? 0 : config.getExtraMagmaSpawnRate();
			if(rng.nextInt(1000000) < wither) {
				event.setCancelled(true);
				world.spawnEntity(loc, EntityType.WITHER_SKELETON);
			} else if(rng.nextInt(1000000) < ghast) {
				event.setCancelled(true);
				int x = loc.getBlockX();
				int z = loc.getBlockZ();
				List<Integer> heights = new ArrayList<Integer>(16);
				int last = 2;
				int empty = 0;
				int max = world.getMaxHeight();
				for(int y = 2; y < max; y++) {
					block = world.getBlockAt(x, y, z);
					if(block.isEmpty()) {
						if(++empty == 11) {
							heights.add(last + 2);
						}
					} else {
						last = y;
						empty = 0;
					}
				}
				if(heights.size() <= 0) {
					return;
				}
				loc.setY(heights.get(rng.nextInt(heights.size())));
				world.spawnEntity(loc, EntityType.GHAST);
			} else if(rng.nextInt(1000000) < magma) {
				event.setCancelled(true);
				world.spawnEntity(loc, EntityType.MAGMA_CUBE);
			}
		}
	}
	
	@EventHandler
	public void adjustWitherSkulls(EntityDeathEvent event) {
		if(config.isEnabled() && event.getEntity() instanceof WitherSkeleton) {
			int rate = config.getWitherSkullDropRate();
			if(rate < 0 || rate > 1000000) return;
			List<ItemStack> drops = event.getDrops();
			for(int i = 0; i < drops.size(); i++) {
				if(drops.get(i).getType() == Material.SKULL_ITEM) {
					drops.remove(i);
				}
			}
			if(rng.nextInt(1000000) < rate) {
				ItemStack skull = new ItemStack(Material.SKULL_ITEM);
				skull.setAmount(1);
				skull.setDurability((short)1);
				drops.add(skull);
			}
		}
	}
	
	@EventHandler
	public void onPlayerEatGoldenApple(PlayerInteractEvent event) {
		if(config.isEnabled() && !config.allowEnchantedApples()) {
			if(event.getItem() != null && event.getItem().getType() == Material.GOLDEN_APPLE
					&& event.getItem().getDurability() == 1) {
				event.getItem().setDurability((short) 1);
			}
		}
	}
	
	@EventHandler
	public void onPlayerMove(PlayerMoveEvent event) {
		if(config.isEnabled() && config.isPreventFallingThroughBedrock() && event.getTo().getY() < 1
				&& event.getPlayer().getGameMode() == GameMode.SURVIVAL) {
			TeleportUtil.tryToTeleportVertically(event.getPlayer(), event.getTo(), "falling into the void");
		}
	}
}
