package bankequipmentstatfilter;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.http.api.item.ItemStats;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.stream.Collectors;

@PluginDescriptor(
		name = "Bank Equipment Stat Filter",
		description = "Allows to filter/sort for equipment slot/stat",
		tags = {"bank", "stat", "equipment", "filter"}
)
@Slf4j
public class BankEquipmentStatFilterPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private EquipmentBankViewService bankViewService;

	@Inject
	private EquipmentBankPlaceholderOverlay bankPlaceholderOverlay;

	@Inject
	private EquipmentBankSectionOverlay bankSectionOverlay;

	@Inject
	private OverlayManager overlayManager;

	private BankEquipmentStatFilterPanel panel;

	private NavigationButton navButton;

	private ItemWithStat[] items;

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getItemContainer() == client.getItemContainer(InventoryID.BANK))
		{
			bankViewService.setBankOpen(true);
			Item[] bankItems = event.getItemContainer().getItems();

			items = Arrays.stream(bankItems)
					.map(item -> {
						ItemStats stats = itemManager.getItemStats(item.getId(), false);
						ItemComposition composition = itemManager.getItemComposition(item.getId());
						if (stats == null || !stats.isEquipable()) {
							return null;
						}
						return new ItemWithStat(item.getId(), stats, composition.getName());
					})
					.filter(Objects::nonNull)
					.toArray(ItemWithStat[]::new);
			if (panel != null)
			{
				panel.refreshResults();
			}
		}
	}

	@Override
	protected void startUp()
	{
		bankViewService.startUp();
		overlayManager.add(bankPlaceholderOverlay);
		overlayManager.add(bankSectionOverlay);
		panel = injector.getInstance(BankEquipmentStatFilterPanel.class);
		panel.refreshResults();

		final BufferedImage icon = ImageUtil.loadImageResource(BankEquipmentStatFilterPlugin.class, "pluginIcon.png");

		navButton = NavigationButton.builder()
				.tooltip("Bank Equipment Stat Filtering")
				.icon(icon)
				.panel(panel)
				.priority(6)
				.build();

		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown()
	{
		bankViewService.shutDown();
		overlayManager.remove(bankPlaceholderOverlay);
		overlayManager.remove(bankSectionOverlay);
		clientToolbar.removeNavigation(navButton);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			bankViewService.setBankOpen(true);
			bankViewService.restoreTemporaryViewIfNeeded();
			if (panel != null)
			{
				panel.refreshViewInBankButtons();
			}
		}
	}

	@Subscribe(priority = 1.0f)
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN && event.isUnload())
		{
			bankViewService.closeTemporaryView();
			bankViewService.setBankOpen(false);
			if (panel != null)
			{
				panel.refreshViewInBankButtons();
			}
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			bankViewService.setBankOpen(false);
			bankViewService.clearRetainedTemporaryView();
			if (panel != null)
			{
				panel.refreshViewInBankButtons();
			}
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if ("bankequipmentstatfilter".equals(event.getGroup())
			&& "keepTemporaryBankView".equals(event.getKey()))
		{
			bankViewService.onKeepTemporaryViewChanged();
		}
	}

	@Subscribe(priority = -1.0f)
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() == ScriptID.BANKMAIN_FINISHBUILDING)
		{
			bankViewService.applyFriendlyBankTitle();
		}
	}

	@Subscribe
	public void onPluginChanged(PluginChanged event)
	{
		if (event.getPlugin() instanceof BankTagsPlugin)
		{
			bankViewService.refreshBankTagsIntegration();
			if (panel != null)
			{
				panel.refreshViewInBankButtons();
			}
		}
	}

	public void filterByStat(EquipmentInventorySlot selectedSlot, EquipmentStat stat, boolean allSlots)
	{
		if (items == null)
		{
			panel.displayMessage("You need to open your bank once so the plugin can sync with it");
			return;
		}

		List<EquipmentResultSection> sections = new ArrayList<>();
		for (EquipmentInventorySlot slot : EquipmentInventorySlot.values())
		{
			if (!allSlots && slot != selectedSlot)
			{
				continue;
			}
			List<ItemWithStat> sectionItems = getSortedItems(slot, stat);
			if (!sectionItems.isEmpty())
			{
				String slotName = formatSlotName(slot);
				sections.add(new EquipmentResultSection(slot.name(),
					slotName + " — " + stat.getDisplayName(), stat, sectionItems));
			}
		}
		panel.displaySections(sections, allSlots, "All — " + stat.getDisplayName());
	}

	public void filterBySlot(EquipmentInventorySlot slot, EquipmentStat selectedStat, boolean allStats)
	{
		if (items == null)
		{
			panel.displayMessage("You need to open your bank once so the plugin can sync with it");
			return;
		}

		List<EquipmentResultSection> sections = new ArrayList<>();
		for (EquipmentStat stat : EquipmentStat.values())
		{
			if (!allStats && stat != selectedStat)
			{
				continue;
			}
			List<ItemWithStat> sectionItems = getSortedItems(slot, stat);
			if (!sectionItems.isEmpty())
			{
				sections.add(new EquipmentResultSection(stat.getDisplayName().toUpperCase(Locale.ROOT),
					stat.getDisplayName() + " — " + formatSlotName(slot), stat, sectionItems));
			}
		}
		panel.displaySections(sections, allStats, "All — " + formatSlotName(slot));
	}

	private List<ItemWithStat> getSortedItems(EquipmentInventorySlot slot, EquipmentStat stat)
	{
		return Arrays.stream(items)
			.filter(item -> item.getStats().getEquipment().getSlot() == slot.getSlotIdx())
			.filter(item -> getItemStat(item.getStats(), stat) > 0)
			.sorted(Comparator.comparing(
				item -> getItemStat(item.getStats(), stat), Comparator.reverseOrder()))
			.collect(Collectors.toList());
	}

	private static String formatSlotName(EquipmentInventorySlot slot)
	{
		String name = slot.name().toLowerCase(Locale.ROOT);
		return Character.toUpperCase(name.charAt(0)) + name.substring(1);
	}

	public int getItemStat(ItemStats stats, EquipmentStat stat)
	{
		if (stats == null || !stats.isEquipable())
		{
			return -1;
		}
		switch (stat) {
			case STAB_ATTACK:
				return stats.getEquipment().getAstab();
			case SLASH_ATTACK:
				return stats.getEquipment().getAslash();
			case CRUSH_ATTACK:
				return stats.getEquipment().getAcrush();
			case MAGIC_ATTACK:
				return stats.getEquipment().getAmagic();
			case RANGE_ATTACK:
				return stats.getEquipment().getArange();
			case STAB_DEFENCE:
				return stats.getEquipment().getDstab();
			case SLASH_DEFENCE:
				return stats.getEquipment().getDslash();
			case CRUSH_DEFENCE:
				return stats.getEquipment().getDcrush();
			case MAGIC_DEFENCE:
				return stats.getEquipment().getDmagic();
			case RANGE_DEFENCE:
				return stats.getEquipment().getDrange();
			case MELEE_STRENGTH:
				return stats.getEquipment().getStr();
			case RANGE_STRENGTH:
				return stats.getEquipment().getRstr();
			case MAGIC_DAMAGE:
				return stats.getEquipment().getMdmg();
			case PRAYER:
				return stats.getEquipment().getPrayer();
			default:
				return -1;
		}
	}

	@Provides
	BankEquipmentStatFilterConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BankEquipmentStatFilterConfig.class);
	}
}
