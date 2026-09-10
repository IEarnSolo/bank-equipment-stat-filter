package bankequipmentstatfilter;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;

@Singleton
@Slf4j
public class BankEquipmentStatFilterPanel extends PluginPanel
{
    private static final int COLUMN_SIZE = 5;
    private static final int ICON_WIDTH = 36;
    private static final int ICON_HEIGHT = 32;

    private final ItemManager itemManager;
    private final BankEquipmentStatFilterPlugin plugin;
    private final BankEquipmentStatFilterConfig config;
    private final EquipmentBankViewService bankViewService;
    private final JPanel itemsPanel = new JPanel();
    private final JTabbedPane searchTabs = new JTabbedPane();
    private final JComboBox<EquipmentStat> byStatStatDropDown = new JComboBox<>(EquipmentStat.values());
    private final JComboBox<EquipmentInventorySlot> byStatSlotDropDown = new JComboBox<>(EquipmentInventorySlot.values());
    private final JCheckBox showAllSlots = new JCheckBox("Show all slots", true);
    private final JComboBox<EquipmentInventorySlot> bySlotSlotDropDown = new JComboBox<>(EquipmentInventorySlot.values());
    private final JComboBox<EquipmentStat> bySlotStatDropDown = new JComboBox<>(EquipmentStat.values());
    private final JCheckBox showAllStats = new JCheckBox("Show all stats", true);
    private boolean controlsReady;

    @Inject
    BankEquipmentStatFilterPanel(ItemManager itemManager, BankEquipmentStatFilterPlugin plugin,
        BankEquipmentStatFilterConfig config, EquipmentBankViewService bankViewService)
    {
        this.itemManager = itemManager;
        this.plugin = plugin;
        this.config = config;
        this.bankViewService = bankViewService;
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        configureDropDown(byStatStatDropDown, new EquipmentStatDropdownRenderer());
        configureDropDown(byStatSlotDropDown, new SlotDropdownRenderer());
        configureDropDown(bySlotSlotDropDown, new SlotDropdownRenderer());
        configureDropDown(bySlotStatDropDown, new EquipmentStatDropdownRenderer());
        showAllSlots.setFocusable(false);
        showAllStats.setFocusable(false);
        byStatSlotDropDown.setEnabled(false);
        bySlotStatDropDown.setEnabled(false);

        searchTabs.addTab("Search by stat", createByStatControls());
        searchTabs.addTab("Search by slot", createBySlotControls());
        searchTabs.putClientProperty("JTabbedPane.tabAreaAlignment", "center");
        searchTabs.putClientProperty("JTabbedPane.tabAlignment", "center");
        searchTabs.setToolTipTextAt(0, "Choose a stat to compare your best banked equipment across slots.");
        searchTabs.setToolTipTextAt(1, "Choose an equipment slot to compare your best banked equipment across stats.");
        add(searchTabs, BorderLayout.NORTH);

        itemsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        itemsPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        itemsPanel.setLayout(new BoxLayout(itemsPanel, BoxLayout.Y_AXIS));
        add(itemsPanel, BorderLayout.CENTER);

        installListeners();
        controlsReady = true;
    }

    private JPanel createByStatControls()
    {
        JPanel panel = selectionPanel();
        panel.add(new JLabel("Equipment stat: "));
        panel.add(byStatStatDropDown);
        panel.add(new JLabel("Equipment slot: "));
        panel.add(byStatSlotDropDown);
        panel.add(showAllSlots);
        panel.add(new JLabel());
        return panel;
    }

    private JPanel createBySlotControls()
    {
        JPanel panel = selectionPanel();
        panel.add(new JLabel("Equipment slot: "));
        panel.add(bySlotSlotDropDown);
        panel.add(new JLabel("Equipment stat: "));
        panel.add(bySlotStatDropDown);
        panel.add(showAllStats);
        panel.add(new JLabel());
        return panel;
    }

    private JPanel selectionPanel()
    {
        JPanel panel = new JPanel(new GridLayout(0, 2));
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setBorder(new EmptyBorder(10, 0, 10, 0));
        return panel;
    }

    private static void configureDropDown(JComboBox<?> dropDown, DefaultListCellRenderer renderer)
    {
        dropDown.setFocusable(false);
        dropDown.setRenderer(renderer);
    }

    private void installListeners()
    {
        byStatStatDropDown.addActionListener(event -> refreshResults());
        byStatSlotDropDown.addActionListener(event -> refreshResults());
        bySlotSlotDropDown.addActionListener(event -> refreshResults());
        bySlotStatDropDown.addActionListener(event -> refreshResults());
        showAllSlots.addActionListener(event ->
        {
            byStatSlotDropDown.setEnabled(!showAllSlots.isSelected());
            refreshResults();
        });
        showAllStats.addActionListener(event ->
        {
            bySlotStatDropDown.setEnabled(!showAllStats.isSelected());
            refreshResults();
        });
        searchTabs.addChangeListener(event -> refreshResults());
    }

    public void refreshResults()
    {
        if (!controlsReady)
        {
            return;
        }
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(this::refreshResults);
            return;
        }
        if (searchTabs.getSelectedIndex() == 0)
        {
            plugin.filterByStat((EquipmentInventorySlot) byStatSlotDropDown.getSelectedItem(),
                (EquipmentStat) byStatStatDropDown.getSelectedItem(), showAllSlots.isSelected());
        }
        else
        {
            plugin.filterBySlot((EquipmentInventorySlot) bySlotSlotDropDown.getSelectedItem(),
                (EquipmentStat) bySlotStatDropDown.getSelectedItem(), showAllStats.isSelected());
        }
    }

    public void displaySections(List<EquipmentResultSection> sections, boolean limitSections,
        String combinedBankViewName)
    {
        itemsPanel.removeAll();
        List<EquipmentResultSection> displayedSections = sections.stream()
            .map(section -> limitSection(section, limitSections))
            .filter(section -> !section.getItems().isEmpty())
            .collect(Collectors.toList());
        if (displayedSections.isEmpty())
        {
            displayMessage("No items found.");
            return;
        }

        addViewAllInBankButton(displayedSections, combinedBankViewName);
        displayedSections.forEach(this::paintSection);
        repaint();
        revalidate();
    }

    private EquipmentResultSection limitSection(EquipmentResultSection section, boolean limit)
    {
        if (!limit)
        {
            return section;
        }
        List<ItemWithStat> items = section.getItems().stream().limit(config.maxItemsPerSlot())
            .collect(Collectors.toList());
        return new EquipmentResultSection(section.getName(), section.getBankViewName(), section.getStat(), items);
    }

    private void addViewAllInBankButton(List<EquipmentResultSection> sections, String combinedBankViewName)
    {
        List<EquipmentBankViewService.EquipmentSection> bankSections = sections.stream()
            .map(section -> new EquipmentBankViewService.EquipmentSection(
                section.getName(), section.getItems(), createBonusList(section)))
            .collect(Collectors.toList());
        List<Integer> itemIds = sections.stream().flatMap(section -> section.getItems().stream())
            .map(ItemWithStat::getId).collect(Collectors.toList());
        JButton button = viewInBankButton("View all in Bank", itemIds);
        button.addActionListener(event -> bankViewService.viewSections(combinedBankViewName, bankSections));
        itemsPanel.add(buttonPanel(button));
    }

    private void paintSection(EquipmentResultSection section)
    {
        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.add(new JLabel(section.getName(), SwingConstants.CENTER), BorderLayout.CENTER);
        itemsPanel.add(titlePanel);

        JPanel itemContainer = new JPanel(new GridLayout(0, COLUMN_SIZE, 1, 1));
        itemContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        itemContainer.setBorder(new EmptyBorder(10, 10, 10, 10));
        for (ItemWithStat item : section.getItems())
        {
            JPanel itemPanel = new JPanel();
            itemPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            JLabel itemLabel = new JLabel();
            itemLabel.setHorizontalAlignment(SwingConstants.CENTER);
            itemLabel.setVerticalAlignment(SwingConstants.CENTER);
            AsyncBufferedImage icon = itemManager.getImage(item.getId());
            icon.addTo(itemLabel);
            itemLabel.setSize(icon.getWidth(), icon.getHeight());
            itemLabel.setMaximumSize(new Dimension(ICON_WIDTH, ICON_HEIGHT));
            itemLabel.setToolTipText(String.format("%s (+%s)", item.getName(),
                plugin.getItemStat(item.getStats(), section.getStat())));
            itemPanel.add(itemLabel);
            itemContainer.add(itemPanel);
        }
        int remainder = section.getItems().size() % COLUMN_SIZE;
        for (int i = 0; remainder != 0 && i < COLUMN_SIZE - remainder; i++)
        {
            JPanel filler = new JPanel();
            filler.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            itemContainer.add(filler);
        }
        itemsPanel.add(itemContainer);

        List<Integer> itemIds = section.getItems().stream().map(ItemWithStat::getId)
            .collect(Collectors.toList());
        JButton button = viewInBankButton("View in Bank", itemIds);
        List<ItemWithStat> displayedItems = Collections.unmodifiableList(new ArrayList<>(section.getItems()));
        List<Integer> bonuses = createBonusList(section);
        button.addActionListener(event -> bankViewService.viewItems(section.getBankViewName(), displayedItems, bonuses));
        itemsPanel.add(buttonPanel(button));
    }

    private JButton viewInBankButton(String text, List<Integer> itemIds)
    {
        JButton button = new JButton(text);
        button.setFocusable(false);
        button.putClientProperty("bankequipmentstatfilter.itemIds", itemIds);
        applyViewInBankButtonState(button);
        return button;
    }

    private JPanel buttonPanel(JButton button)
    {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setBorder(new EmptyBorder(0, 0, 8, 0));
        panel.add(button);
        return panel;
    }

    private List<Integer> createBonusList(EquipmentResultSection section)
    {
        return section.getItems().stream()
            .map(item -> plugin.getItemStat(item.getStats(), section.getStat()))
            .collect(Collectors.toList());
    }

    public void refreshViewInBankButtons()
    {
        SwingUtilities.invokeLater(() ->
        {
            refreshViewInBankButtons(itemsPanel);
            itemsPanel.repaint();
        });
    }

    private void refreshViewInBankButtons(Component component)
    {
        if (component instanceof JButton)
        {
            JButton button = (JButton) component;
            if (button.getClientProperty("bankequipmentstatfilter.itemIds") != null)
            {
                applyViewInBankButtonState(button);
            }
        }
        if (component instanceof Container)
        {
            for (Component child : ((Container) component).getComponents())
            {
                refreshViewInBankButtons(child);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void applyViewInBankButtonState(JButton button)
    {
        Collection<Integer> itemIds = (Collection<Integer>) button.getClientProperty("bankequipmentstatfilter.itemIds");
        EquipmentBankViewService.Availability availability = bankViewService.getAvailability(itemIds);
        button.setEnabled(availability == EquipmentBankViewService.Availability.AVAILABLE);
        button.setForeground(button.isEnabled() ? ColorScheme.TEXT_COLOR : Color.GRAY);
        button.setToolTipText(availability.getReason());
    }

    public void displayMessage(String message)
    {
        itemsPanel.removeAll();
        JTextArea textArea = new JTextArea(message);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setFocusable(false);
        textArea.setEditable(false);
        textArea.setBackground(ColorScheme.DARK_GRAY_COLOR);
        itemsPanel.add(textArea);
        repaint();
        revalidate();
    }

    private static class EquipmentStatDropdownRenderer extends DefaultListCellRenderer
    {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
            boolean isSelected, boolean cellHasFocus)
        {
            Object displayValue = value instanceof EquipmentStat ? ((EquipmentStat) value).getDisplayName() : value;
            return super.getListCellRendererComponent(list, displayValue, index, isSelected, cellHasFocus);
        }
    }

    private static class SlotDropdownRenderer extends DefaultListCellRenderer
    {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
            boolean isSelected, boolean cellHasFocus)
        {
            Object displayValue = value;
            if (value instanceof EquipmentInventorySlot)
            {
                String name = value.toString().toLowerCase();
                displayValue = Character.toUpperCase(name.charAt(0)) + name.substring(1);
            }
            return super.getListCellRendererComponent(list, displayValue, index, isSelected, cellHasFocus);
        }
    }
}
