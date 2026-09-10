package bankequipmentstatfilter;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

@Singleton
public class EquipmentBankSectionOverlay extends Overlay
{
    private final Client client;
    private final EquipmentBankViewService bankViewService;

    @Inject
    public EquipmentBankSectionOverlay(Client client, EquipmentBankViewService bankViewService)
    {
        this.client = client;
        this.bankViewService = bankViewService;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        Widget itemContainer = client.getWidget(InterfaceID.Bankmain.ITEMS);
        if (itemContainer == null || itemContainer.isHidden())
        {
            return null;
        }

        Map<Integer, String> headers = bankViewService.getActiveSectionHeaders();
        if (headers.isEmpty())
        {
            return null;
        }

        Rectangle clip = itemContainer.getBounds();
        graphics.setFont(FontManager.getRunescapeSmallFont());
        for (Map.Entry<Integer, String> header : headers.entrySet())
        {
            Widget headerCell = itemContainer.getChild(header.getKey());
            if (headerCell == null || headerCell.isHidden())
            {
                continue;
            }

            Point location = headerCell.getCanvasLocation();
            int x = location.getX();
            int y = location.getY() + 30;
            if (clip != null && (y < clip.y || y > clip.y + clip.height))
            {
                continue;
            }

            graphics.setColor(Color.BLACK);
            graphics.drawString(header.getValue(), x + 1, y + 1);
            graphics.setColor(Color.LIGHT_GRAY);
            graphics.drawString(header.getValue(), x, y);
        }
        return null;
    }
}
