package gt.cadprint.manager;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/customers")
class CustomerController {
    private final CustomerRepository customers;

    CustomerController(CustomerRepository customers) {
        this.customers = customers;
    }

    @GetMapping
    List<CustomerView> list() {
        return customers.findAllByOrderByNameAscIdAsc().stream().map(CustomerView::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CustomerView create(@RequestBody @Valid CustomerInput input) {
        return save(new Customer(), input);
    }

    @PutMapping("/{id}")
    CustomerView update(@PathVariable Long id, @RequestBody @Valid CustomerInput input) {
        return save(customers.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado")), input);
    }

    private CustomerView save(Customer customer, CustomerInput input) {
        customer.name = input.name().strip();
        customer.contact = clean(input.contact());
        customer.email = clean(input.email());
        customer.phone = clean(input.phone());
        customer.address = clean(input.address());
        return CustomerView.from(customers.save(customer));
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    record CustomerInput(@NotBlank @Size(max = 255) String name, @Size(max = 255) String contact,
                         @Email @Size(max = 254) String email, @Size(max = 64) String phone,
                         @Size(max = 500) String address) {}

    record CustomerView(Long id, String name, String contact, String email, String phone, String address) {
        static CustomerView from(Customer customer) {
            return new CustomerView(customer.id, customer.name, customer.contact, customer.email,
                    customer.phone, customer.address);
        }
    }
}
