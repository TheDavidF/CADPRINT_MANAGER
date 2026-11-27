package gt.cadprint.manager;

import java.util.List;
import java.util.Locale;

import gt.cadprint.manager.ProductionCostDtos.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static gt.cadprint.manager.ProductionCostMath.*;

@Service
@Transactional(readOnly = true)
class ProductionCostSettings {
    private final ProductionCostProfileRepo profiles;
    private final ProductionMachineRepo machines;
    private final EntityManager entityManager;

    ProductionCostSettings(ProductionCostProfileRepo profiles, ProductionMachineRepo machines, EntityManager entityManager) {
        this.profiles = profiles;
        this.machines = machines;
        this.entityManager = entityManager;
    }

    List<ProfileView> profiles() {
        return profiles.findByActiveOrderByCodeAsc(true).stream().map(this::profileView).toList();
    }

    List<MachineView> machines(ProductionTechnique technique) {
        return machines.findByTechniqueAndActiveOrderByCodeAsc(technique, true).stream().map(this::machineView).toList();
    }

    @Transactional
    ProfileView saveProfile(Long id, ProfileInput input, String actor) {
        ProductionCostProfile profile = id == null ? new ProductionCostProfile()
                : locked(ProductionCostProfile.class, id, "Perfil");
        if (id != null) checkVersion(input.version(), profile.version);
        profile.code = code(input.code());
        profile.name = input.name().strip();
        profile.electricityRatePerKwh = input.electricityRatePerKwh();
        profile.laborRatePerHour = input.laborRatePerHour();
        profile.indirectRatePerHour = input.indirectRatePerHour();
        profile.active = input.active();
        profile.updatedBy = actor;
        return profileView(profiles.saveAndFlush(profile));
    }

    @Transactional
    MachineView saveMachine(Long id, MachineInput input, String actor) {
        ProductionMachine machine = id == null ? new ProductionMachine()
                : locked(ProductionMachine.class, id, "Máquina");
        if (id != null) {
            checkVersion(input.version(), machine.version);
            if (machine.technique != input.technique()) {
                throw conflict("La técnica de una máquina registrada no puede cambiar");
            }
        }
        machine.code = code(input.code());
        machine.name = input.name().strip();
        machine.technique = input.technique();
        machine.nominalPowerWatts = input.nominalPowerWatts();
        machine.machineRatePerHour = input.machineRatePerHour();
        machine.active = input.active();
        machine.updatedBy = actor;
        return machineView(machines.saveAndFlush(machine));
    }

    private String code(String raw) {
        String canonical = raw.strip().toUpperCase(Locale.ROOT);
        if (canonical.length() > 64) {
            throw invalid("El código normalizado excede 64 caracteres");
        }
        return canonical;
    }

    private void checkVersion(Long supplied, Long current) {
        if (supplied == null) throw invalid("La versión es obligatoria");
        if (!supplied.equals(current)) throw conflict("La versión del registro está desactualizada");
    }

    private <T> T locked(Class<T> type, Long id, String label) {
        T result = entityManager.find(type, id, LockModeType.PESSIMISTIC_WRITE);
        if (result == null) throw missing(label + " no encontrado: " + id);
        return result;
    }

    private ProfileView profileView(ProductionCostProfile profile) {
        return new ProfileView(profile.id, profile.code, profile.name, profile.currency, profile.electricityRatePerKwh,
                profile.laborRatePerHour, profile.indirectRatePerHour, profile.active, profile.version);
    }

    private MachineView machineView(ProductionMachine machine) {
        return new MachineView(machine.id, machine.code, machine.name, machine.technique, machine.nominalPowerWatts,
                machine.machineRatePerHour, machine.active, machine.version);
    }
}
