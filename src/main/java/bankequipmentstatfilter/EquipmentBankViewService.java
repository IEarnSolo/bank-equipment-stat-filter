package bankequipmentstatfilter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.ui.JagexColors;
import net.runelite.client.util.Text;

@Slf4j
@Singleton
public class EquipmentBankViewService
{
    private static final int BANK_ITEMS_PER_ROW = BankTagsPlugin.BANK_ITEMS_PER_ROW;

    public static final class EquipmentSection
    {
        private final String name;
        private final List<ItemWithStat> items;
        private final List<Integer> bonuses;

        public EquipmentSection(String name, List<ItemWithStat> items, List<Integer> bonuses)
        {
            this.name = name;
            this.items = Collections.unmodifiableList(new ArrayList<>(items));
            this.bonuses = Collections.unmodifiableList(new ArrayList<>(bonuses));
        }

        public String getName()
        {
            return name;
        }

        public List<ItemWithStat> getItems()
        {
            return items;
        }

        public List<Integer> getBonuses()
        {
            return bonuses;
        }
    }

    public enum Availability
    {
        AVAILABLE(null),
        BANK_CLOSED("Open your bank to view these items"),
        BANK_TAGS_INACTIVE("Enable the Bank Tags plugin to view these items in your bank"),
        NO_ITEMS("No equipment items are available to view");

        private final String reason;

        Availability(String reason)
        {
            this.reason = reason;
        }

        public String getReason()
        {
            return reason;
        }
    }

    private static final String TEMP_TAG = "bank-equipment-stat-filter-view";
    private static final String LAYOUT_KEY = BankTagsPlugin.TAG_LAYOUT_PREFIX + Text.standardize(TEMP_TAG);
    private static final String BANK_TAGS_ACTIVE_TAB_KEY = "tab";

    private final Client client;
    private final ClientThread clientThread;
    private final ConfigManager configManager;
    private final PluginManager pluginManager;
    private final ItemManager itemManager;
    private final BankEquipmentStatFilterConfig config;

    private volatile boolean bankOpen;
    private volatile BankTagsPlugin bankTagsPlugin;
    private volatile String activeViewName;
    private volatile List<Integer> activeItemIds = Collections.emptyList();
    private volatile Map<Integer, String> activePlaceholderTooltips = Collections.emptyMap();
    private volatile Map<Integer, Integer> activeItemBonuses = Collections.emptyMap();
    private volatile Map<Integer, String> activeSectionHeaders = Collections.emptyMap();
    private volatile boolean restoreOnNextBankOpen;

    @Inject
    public EquipmentBankViewService(Client client, ClientThread clientThread, ConfigManager configManager,
        PluginManager pluginManager, ItemManager itemManager, BankEquipmentStatFilterConfig config)
    {
        this.client = client;
        this.clientThread = clientThread;
        this.configManager = configManager;
        this.pluginManager = pluginManager;
        this.itemManager = itemManager;
        this.config = config;
    }

    public void startUp()
    {
        clearRememberedTemporaryTab();
        refreshBankTagsIntegration();
        clientThread.invokeLater(() ->
        {
            Widget bank = client.getWidget(InterfaceID.Bankmain.ITEMS);
            setBankOpen(bank != null && !bank.isHidden());
        });
    }

    public void shutDown()
    {
        BankTagsPlugin plugin = bankTagsPlugin;
        if (plugin != null && pluginManager.isPluginActive(plugin)
            && activeViewName != null && TEMP_TAG.equals(plugin.getActiveTag()))
        {
            clientThread.invokeLater(plugin::closeBankTag);
        }
        clearRememberedTemporaryTab();
        bankTagsPlugin = null;
        clearTemporaryState();
        bankOpen = false;
    }

    public boolean refreshBankTagsIntegration()
    {
        BankTagsPlugin previous = bankTagsPlugin;
        Optional<BankTagsPlugin> active = pluginManager.getPlugins().stream()
            .filter(BankTagsPlugin.class::isInstance)
            .map(BankTagsPlugin.class::cast)
            .filter(pluginManager::isPluginActive)
            .findFirst();
        bankTagsPlugin = active.orElse(null);
        if (bankTagsPlugin == null)
        {
            clearRememberedTemporaryTab();
            clearTemporaryState();
        }
        return previous != bankTagsPlugin;
    }

    public void setBankOpen(boolean open)
    {
        bankOpen = open;
    }

    public Availability getAvailability(Collection<Integer> itemIds)
    {
        if (itemIds == null || itemIds.isEmpty())
        {
            return Availability.NO_ITEMS;
        }
        if (!bankOpen)
        {
            return Availability.BANK_CLOSED;
        }
        if (bankTagsPlugin == null || !pluginManager.isPluginActive(bankTagsPlugin))
        {
            return Availability.BANK_TAGS_INACTIVE;
        }
        return Availability.AVAILABLE;
    }

    public void viewItems(String viewName, List<ItemWithStat> items, List<Integer> bonuses)
    {
        List<Integer> itemIds = items.stream().map(ItemWithStat::getId).collect(Collectors.toList());
        Availability availability = getAvailability(itemIds);
        if (availability != Availability.AVAILABLE)
        {
            return;
        }

        Map<Integer, String> placeholderTooltips = new LinkedHashMap<>();
        for (ItemWithStat item : items)
        {
            placeholderTooltips.put(item.getId(), JagexColors.MENU_TARGET_TAG + item.getName() + "</col>");
        }

        String normalizedViewName = viewName == null || viewName.trim().isEmpty()
            ? "Equipment" : viewName.trim();
        Map<Integer, Integer> layoutBonuses = new LinkedHashMap<>();
        for (int i = 0; i < bonuses.size(); i++)
        {
            layoutBonuses.put(i, bonuses.get(i));
        }
        clientThread.invoke(() -> openView(normalizedViewName, itemIds, placeholderTooltips,
            Collections.emptyMap(), layoutBonuses));
    }

    public void viewSections(String viewName, List<EquipmentSection> sections)
    {
        List<Integer> layout = new ArrayList<>();
        Map<Integer, String> sectionHeaders = new LinkedHashMap<>();
        Map<Integer, String> placeholderTooltips = new LinkedHashMap<>();
        Map<Integer, Integer> layoutBonuses = new LinkedHashMap<>();

        for (EquipmentSection section : sections)
        {
            while (layout.size() % BANK_ITEMS_PER_ROW != 0)
            {
                layout.add(-1);
            }

            int headerSlot = layout.size();
            sectionHeaders.put(headerSlot, section.getName());
            for (int i = 0; i < BANK_ITEMS_PER_ROW; i++)
            {
                layout.add(-1);
            }

            for (int i = 0; i < section.getItems().size(); i++)
            {
                ItemWithStat item = section.getItems().get(i);
                layoutBonuses.put(layout.size(), section.getBonuses().get(i));
                layout.add(item.getId());
                placeholderTooltips.put(item.getId(),
                    JagexColors.MENU_TARGET_TAG + item.getName() + "</col>");
            }
        }

        Availability availability = getAvailability(layout.stream()
            .filter(itemId -> itemId >= 0)
            .collect(Collectors.toList()));
        if (availability != Availability.AVAILABLE)
        {
            return;
        }

        clientThread.invoke(() -> openView(viewName, layout, placeholderTooltips, sectionHeaders,
            layoutBonuses));
    }

    private void openView(String displayName, List<Integer> itemIds, Map<Integer, String> placeholderTooltips,
        Map<Integer, String> sectionHeaders, Map<Integer, Integer> itemBonuses)
    {
        BankTagsPlugin plugin = bankTagsPlugin;
        Widget bank = client.getWidget(InterfaceID.Bankmain.ITEMS);
        if (plugin == null || !pluginManager.isPluginActive(plugin) || bank == null || bank.isHidden())
        {
            return;
        }

        Set<Integer> acceptedIds = itemIds.stream()
            .filter(itemId -> itemId >= 0)
            .map(itemManager::canonicalize)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Integer, String> canonicalTooltips = new LinkedHashMap<>();
        placeholderTooltips.forEach((itemId, tooltip) ->
            canonicalTooltips.put(itemManager.canonicalize(itemId), tooltip));

        TagManager tagManager = plugin.getInjector().getInstance(TagManager.class);
        String previousLayout = configManager.getConfiguration(BankTagsPlugin.CONFIG_GROUP, LAYOUT_KEY);
        String layout = itemIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        try
        {
            tagManager.registerTag(TEMP_TAG,
                itemId -> acceptedIds.contains(itemManager.canonicalize(itemId)));
            configManager.setConfiguration(BankTagsPlugin.CONFIG_GROUP, LAYOUT_KEY, layout);
            activeViewName = displayName;
            activeItemIds = Collections.unmodifiableList(new ArrayList<>(itemIds));
            activePlaceholderTooltips = Collections.unmodifiableMap(canonicalTooltips);
            activeItemBonuses = Collections.unmodifiableMap(new LinkedHashMap<>(itemBonuses));
            activeSectionHeaders = Collections.unmodifiableMap(new LinkedHashMap<>(sectionHeaders));
            restoreOnNextBankOpen = false;
            plugin.openBankTag(TEMP_TAG, BankTagsService.OPTION_HIDE_TAG_NAME);
            applyFriendlyBankTitle();
        }
        catch (RuntimeException ex)
        {
            clearTemporaryState();
            log.warn("Failed to open temporary equipment bank view", ex);
        }
        finally
        {
            tagManager.unregisterTag(TEMP_TAG);
            if (previousLayout == null)
            {
                configManager.unsetConfiguration(BankTagsPlugin.CONFIG_GROUP, LAYOUT_KEY);
            }
            else
            {
                configManager.setConfiguration(BankTagsPlugin.CONFIG_GROUP, LAYOUT_KEY, previousLayout);
            }
        }
    }

    public void closeTemporaryView()
    {
        BankTagsPlugin plugin = bankTagsPlugin;
        boolean temporaryViewActive = plugin != null && pluginManager.isPluginActive(plugin)
            && activeViewName != null && TEMP_TAG.equals(plugin.getActiveTag());
        if (!temporaryViewActive)
        {
            if (activeViewName != null)
            {
                clearTemporaryState();
            }
            return;
        }

        if (config.keepTemporaryBankView())
        {
            restoreOnNextBankOpen = true;
            return;
        }

        activePlaceholderTooltips = Collections.emptyMap();
        clientThread.invokeLater(() ->
        {
            if (pluginManager.isPluginActive(plugin))
            {
                plugin.closeBankTag();
                clearTemporaryState();
            }
        });
    }

    public void restoreTemporaryViewIfNeeded()
    {
        if (!restoreOnNextBankOpen || !config.keepTemporaryBankView())
        {
            return;
        }
        String displayName = activeViewName;
        List<Integer> itemIds = activeItemIds;
        Map<Integer, String> placeholderTooltips = activePlaceholderTooltips;
        Map<Integer, String> sectionHeaders = activeSectionHeaders;
        Map<Integer, Integer> itemBonuses = activeItemBonuses;
        restoreOnNextBankOpen = false;
        clientThread.invokeLater(() ->
        {
            if (!bankOpen || displayName == null || itemIds.isEmpty())
            {
                clearTemporaryState();
                return;
            }
            openView(displayName, itemIds, placeholderTooltips, sectionHeaders, itemBonuses);
        });
    }

    public void onKeepTemporaryViewChanged()
    {
        if (!config.keepTemporaryBankView() && !bankOpen && restoreOnNextBankOpen)
        {
            clearRememberedTemporaryTab();
            clearTemporaryState();
        }
    }

    public void clearRetainedTemporaryView()
    {
        clearRememberedTemporaryTab();
        clearTemporaryState();
    }

    public String getFakePlaceholderTooltip(int itemId)
    {
        BankTagsPlugin plugin = bankTagsPlugin;
        if (plugin == null || activeViewName == null || !TEMP_TAG.equals(plugin.getActiveTag()))
        {
            return null;
        }
        return activePlaceholderTooltips.get(itemManager.canonicalize(itemId));
    }

    public Map<Integer, String> getActiveSectionHeaders()
    {
        BankTagsPlugin plugin = bankTagsPlugin;
        if (plugin == null || activeViewName == null || !TEMP_TAG.equals(plugin.getActiveTag()))
        {
            return Collections.emptyMap();
        }
        return activeSectionHeaders;
    }

    public Integer getActiveItemBonus(int layoutPosition)
    {
        BankTagsPlugin plugin = bankTagsPlugin;
        if (plugin == null || activeViewName == null || !TEMP_TAG.equals(plugin.getActiveTag()))
        {
            return null;
        }
        return activeItemBonuses.get(layoutPosition);
    }

    public void applyFriendlyBankTitle()
    {
        BankTagsPlugin plugin = bankTagsPlugin;
        String displayName = activeViewName;
        if (plugin == null || displayName == null || !TEMP_TAG.equals(plugin.getActiveTag()))
        {
            return;
        }
        Widget title = client.getWidget(InterfaceID.Bankmain.TITLE);
        if (title != null)
        {
            title.setText(Text.escapeJagex(displayName));
        }
    }

    private void clearTemporaryState()
    {
        activeViewName = null;
        activeItemIds = Collections.emptyList();
        activePlaceholderTooltips = Collections.emptyMap();
        activeItemBonuses = Collections.emptyMap();
        activeSectionHeaders = Collections.emptyMap();
        restoreOnNextBankOpen = false;
    }

    private void clearRememberedTemporaryTab()
    {
        String rememberedTab = configManager.getConfiguration(
            BankTagsPlugin.CONFIG_GROUP, BANK_TAGS_ACTIVE_TAB_KEY);
        if (TEMP_TAG.equals(rememberedTab))
        {
            configManager.setConfiguration(BankTagsPlugin.CONFIG_GROUP, BANK_TAGS_ACTIVE_TAB_KEY, "");
        }
    }
}
