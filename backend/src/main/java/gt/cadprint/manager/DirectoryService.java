package gt.cadprint.manager;

import java.math.BigDecimal;
import java.util.List;

import gt.cadprint.manager.ApiDtos.Dashboard;
import gt.cadprint.manager.ApiDtos.SupplierRequest;
import gt.cadprint.manager.ApiDtos.SupplierView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class DirectoryService {
    private final SupplierRepo suppliers;
    private final MaterialRepo materials;
    private final MovementRepo movements;

    DirectoryService(SupplierRepo suppliers, MaterialRepo materials, MovementRepo movements) {
        this.suppliers = suppliers;
        this.materials = materials;
        this.movements = movements;
    }

    List<SupplierView> suppliers() {
        return suppliers.findAll().stream().map(SupplierView::from).toList();
    }

    @Transactional
    SupplierView createSupplier(SupplierRequest input) {
        Supplier supplier = new Supplier();
        supplier.name = input.name();
        supplier.phone = input.phone();
        supplier.email = input.email();
        supplier.notes = input.notes();
        return SupplierView.from(suppliers.save(supplier));
    }

    Dashboard dashboard() {
        List<Material> inventory = materials.findAll();
        long lowStock = inventory.stream()
                .filter(material -> material.active && material.stock.compareTo(material.minStock) <= 0)
                .count();
        BigDecimal inventoryValue = inventory.stream()
                .map(material -> material.stock.multiply(material.unitCost))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Dashboard(inventory.size(), lowStock, inventoryValue, movements.count());
    }
}