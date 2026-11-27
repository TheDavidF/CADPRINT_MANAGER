package gt.cadprint.manager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import gt.cadprint.manager.ApiDtos.MaterialChangeView;
import gt.cadprint.manager.ApiDtos.MaterialView;
import org.springframework.stereotype.Service;

@Service
class MaterialAuditService {
    private final MaterialChangeRepo changes;

    MaterialAuditService(MaterialChangeRepo changes) {
        this.changes = changes;
    }

    List<MaterialChangeView> history(Long materialId) {
        return changes.findByMaterialIdOrderByChangedAtDescIdDesc(materialId).stream()
                .map(MaterialChangeView::from)
                .toList();
    }

    void record(Material material, String username, MaterialView before, MaterialView after) {
        Instant changedAt = Instant.now();
        List<MaterialChange> audit = new ArrayList<>();
        add(audit, material, username, changedAt, "name", before.name(), after.name());
        if (!before.categoryId().equals(after.categoryId())) {
            add(audit, material, username, changedAt, "category", before.category(), after.category());
        }
        add(audit, material, username, changedAt, "unit", before.unit(), after.unit());
        add(audit, material, username, changedAt, "minStock", decimal(before.minStock()), decimal(after.minStock()));
        add(audit, material, username, changedAt, "unitCost", decimal(before.unitCost()), decimal(after.unitCost()));
        add(audit, material, username, changedAt, "active",
                Boolean.toString(before.active()), Boolean.toString(after.active()));
        if (!audit.isEmpty()) {
            changes.saveAll(audit);
        }
    }

    private void add(List<MaterialChange> audit, Material material, String username, Instant changedAt,
                     String field, String previousValue, String newValue) {
        if (!Objects.equals(previousValue, newValue)) {
            audit.add(new MaterialChange(material, username, changedAt, field, previousValue, newValue));
        }
    }

    private String decimal(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
