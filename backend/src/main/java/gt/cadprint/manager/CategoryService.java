package gt.cadprint.manager;

import java.util.List;
import java.util.Locale;

import gt.cadprint.manager.ApiDtos.CategoryView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
class CategoryService {
    private final CategoryRepo categories;

    CategoryService(CategoryRepo categories) {
        this.categories = categories;
    }

    List<CategoryView> list() {
        return categories.findAllByOrderByNameKeyAsc().stream().map(CategoryView::from).toList();
    }

    @Transactional
    CategoryView create(String name) {
        return saveName(new Category(), name);
    }

    @Transactional
    CategoryView rename(Long id, String name) {
        Category category = categories.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Categor\u00eda no encontrada"));
        return saveName(category, name);
    }

    Category resolve(Long id, String name) {
        if (id != null) {
            return categories.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categor\u00eda no encontrada"));
        }
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona una categor\u00eda");
        }
        return categories.findByNameKey(normalize(name))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categor\u00eda no encontrada"));
    }

    private CategoryView saveName(Category category, String name) {
        String nameKey = normalize(name);
        categories.findByNameKey(nameKey).filter(existing -> !existing.id.equals(category.id)).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una categor\u00eda con ese nombre");
        });
        category.name = name.strip();
        category.nameKey = nameKey;
        return CategoryView.from(categories.saveAndFlush(category));
    }

    private String normalize(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }
}
