package bankequipmentstatfilter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("bankequipmentstatfilter")
public interface BankEquipmentStatFilterConfig extends Config
{
    @ConfigItem(
            keyName = "maxItemsPerSlot",
            name = "Max items per section",
            description = "Specify the maximum number of items displayed in each section",
            position = 1
    )
    default int maxItemsPerSlot()
    {
        return 7; // Default value
    }

    @ConfigItem(
            keyName = "showStatBonusOverlay",
            name = "Show stat bonus overlay",
            description = "Show the selected stat bonus on items in temporary filtered bank views",
            position = 2
    )
    default boolean showStatBonusOverlay()
    {
        return true;
    }

    @ConfigItem(
            keyName = "keepTemporaryBankView",
            name = "Keep filtered view open",
            description = "Reopen the temporary equipment filter when the bank is closed and reopened",
            position = 3
    )
    default boolean keepTemporaryBankView()
    {
        return false;
    }
}
