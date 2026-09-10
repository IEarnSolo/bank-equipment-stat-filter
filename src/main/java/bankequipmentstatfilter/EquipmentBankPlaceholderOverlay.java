package bankequipmentstatfilter;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.ItemQuantityMode;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.overlay.WidgetItemOverlay;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

@Singleton
public class EquipmentBankPlaceholderOverlay extends WidgetItemOverlay
{
    private final Client client;
    private final EquipmentBankViewService bankViewService;
    private final TooltipManager tooltipManager;
    private final BankEquipmentStatFilterConfig config;

    @Inject
    public EquipmentBankPlaceholderOverlay(Client client, EquipmentBankViewService bankViewService,
        TooltipManager tooltipManager, BankEquipmentStatFilterConfig config)
    {
        this.client = client;
        this.bankViewService = bankViewService;
        this.tooltipManager = tooltipManager;
        this.config = config;
        drawAfterLayer(InterfaceID.Bankmain.ITEMS);
    }

    @Override
    public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
    {
        Widget widget = widgetItem.getWidget();
        if (widget.getId() != InterfaceID.Bankmain.ITEMS)
        {
            return;
        }

        Rectangle bounds = widgetItem.getCanvasBounds();
        if (bounds == null)
        {
            return;
        }

        Integer bonus = bankViewService.getActiveItemBonus(widget.getIndex());
        if (bonus != null && config.showStatBonusOverlay())
        {
            String text = bonus >= 0 ? "+" + bonus : String.valueOf(bonus);
            graphics.setFont(FontManager.getRunescapeSmallFont());
            FontMetrics metrics = graphics.getFontMetrics();
            int x = bounds.x + bounds.width - metrics.stringWidth(text) - 1;
            int y = bounds.y + bounds.height - 1;
            graphics.setColor(Color.BLACK);
            graphics.drawString(text, x + 1, y + 1);
            graphics.setColor(Color.WHITE);
            graphics.drawString(text, x, y);
        }

        if (widget.getItemQuantity() != Integer.MAX_VALUE
            || widget.getItemQuantityMode() != ItemQuantityMode.NEVER)
        {
            return;
        }

        Point mouse = client.getMouseCanvasPosition();
        if (mouse == null || !bounds.contains(mouse.getX(), mouse.getY()))
        {
            return;
        }

        String tooltip = bankViewService.getFakePlaceholderTooltip(itemId);
        if (tooltip != null)
        {
            tooltipManager.add(new Tooltip(tooltip));
        }
    }
}
