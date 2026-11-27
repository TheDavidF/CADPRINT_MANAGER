package gt.cadprint.manager;

import java.net.URI;
import java.util.List;

import gt.cadprint.manager.ProductionCostDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/production-costs")
class ProductionCostController {
    private static final String BASE = "/api/production-costs";
    private final ProductionCostService calculator;
    private final ProductionCostHistory history;
    private final ProductionCostSettings settings;

    ProductionCostController(ProductionCostService calculator, ProductionCostHistory history, ProductionCostSettings settings) {
        this.calculator = calculator;
        this.history = history;
        this.settings = settings;
    }

    @PostMapping("/fdm/preview")
    Preview previewFdm(@Valid @RequestBody FdmInput input, Authentication actor) {
        return calculator.previewFdm(input, actor.getName());
    }

    @PostMapping("/sublimation/preview")
    Preview previewSublimation(@Valid @RequestBody SublimationInput input, Authentication actor) {
        return calculator.previewSublimation(input, actor.getName());
    }

    @PostMapping("/fdm")
    ResponseEntity<HistoryDetail> saveFdm(@Valid @RequestBody FdmSave input, Authentication actor) {
        return created(calculator.saveFdm(input, actor.getName()));
    }

    @PostMapping("/sublimation")
    ResponseEntity<HistoryDetail> saveSublimation(@Valid @RequestBody SublimationSave input, Authentication actor) {
        return created(calculator.saveSublimation(input, actor.getName()));
    }

    @GetMapping("/calculations")
    HistoryPage list(@RequestParam(required = false) ProductionTechnique technique,
                     @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return history.list(technique, page, size);
    }

    @GetMapping("/calculations/{id}")
    HistoryDetail detail(@PathVariable Long id) {
        return history.detail(id);
    }

    @GetMapping("/profiles")
    List<ProfileView> profiles() {
        return settings.profiles();
    }

    @GetMapping("/machines")
    List<MachineView> machines(@RequestParam ProductionTechnique technique) {
        return settings.machines(technique);
    }

    @PostMapping("/profiles")
    ResponseEntity<ProfileView> createProfile(@Valid @RequestBody ProfileInput input, Authentication actor) {
        ProfileView saved = settings.saveProfile(null, input, actor.getName());
        return ResponseEntity.created(URI.create(BASE + "/profiles/" + saved.id())).body(saved);
    }

    @PutMapping("/profiles/{id}")
    ProfileView editProfile(@PathVariable Long id, @Valid @RequestBody ProfileInput input, Authentication actor) {
        return settings.saveProfile(id, input, actor.getName());
    }

    @PostMapping("/machines")
    ResponseEntity<MachineView> createMachine(@Valid @RequestBody MachineInput input, Authentication actor) {
        MachineView saved = settings.saveMachine(null, input, actor.getName());
        return ResponseEntity.created(URI.create(BASE + "/machines/" + saved.id())).body(saved);
    }

    @PutMapping("/machines/{id}")
    MachineView editMachine(@PathVariable Long id, @Valid @RequestBody MachineInput input, Authentication actor) {
        return settings.saveMachine(id, input, actor.getName());
    }

    private ResponseEntity<HistoryDetail> created(HistoryDetail saved) {
        return ResponseEntity.created(URI.create(BASE + "/calculations/" + saved.calculation().id())).body(saved);
    }
}
