package bankequipmentstatfilter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Value;

@Value
public class EquipmentResultSection
{
    String name;
    String bankViewName;
    EquipmentStat stat;
    List<ItemWithStat> items;

    public EquipmentResultSection(String name, String bankViewName, EquipmentStat stat,
                                  List<ItemWithStat> items)
    {
        this.name = name;
        this.bankViewName = bankViewName;
        this.stat = stat;
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
    }
}
