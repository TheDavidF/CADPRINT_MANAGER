package gt.cadprint.manager;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/products/{id}/image")
class ProductImageController {
    private final ProductImageService images;

    ProductImageController(ProductImageService images) {
        this.images = images;
    }

    @GetMapping
    ResponseEntity<byte[]> read(@PathVariable Long id) {
        var image = images.read(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .contentLength(image.bytes().length)
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(image.bytes());
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ProductImageService.ImageUpdate put(@PathVariable Long id, @RequestParam Long version,
                                        @RequestParam MultipartFile file) {
        return images.put(id, version, file);
    }

    @DeleteMapping
    ProductImageService.ImageUpdate delete(@PathVariable Long id, @RequestParam Long version) {
        return images.delete(id, version);
    }
}
