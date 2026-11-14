package fi.agentgate.catalog;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import fi.agentgate.common.ApiException;

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Catalog")
@SecurityRequirement(name = "bearerAuth")
public class ProductController {

    /**
     * Allergen fields use Valio's GS1 codes. {@code allergensKnown=false} means no data at all. A code that is in
     * none of contains / mayContain / freeFrom is UNKNOWN for this product: it must not be read as safe.
     */
    public record ProductView(String code, String name, String category, String unit, BigDecimal lotSize,
                              List<String> allergens, List<String> mayContain, List<String> freeFrom,
                              boolean allergensKnown, String supplier) {

        static ProductView of(Product p) {
            return new ProductView(p.getCode(), p.getName(), p.getCategory(), p.getUnit(), p.getLotSize(),
                    p.allergenList(), p.mayContainList(), p.freeFromList(), p.isAllergensKnown(), p.getSupplier());
        }
    }

    public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
    }

    private final ProductRepository products;

    public ProductController(ProductRepository products) {
        this.products = products;
    }

    @GetMapping
    public PageResponse<ProductView> search(@RequestParam(required = false) String q,
                                            @RequestParam(required = false) String category,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        String pattern = "%" + (q == null ? "" : q.trim().toLowerCase()) + "%";
        String categoryPattern = (category == null || category.isBlank()) ? "%" : category.trim().toLowerCase();
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("name"));
        Page<Product> result = products.search(pattern, categoryPattern, pageable);
        return new PageResponse<>(result.map(ProductView::of).getContent(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @GetMapping("/{code}")
    public ProductView get(@PathVariable String code) {
        return products.findById(code)
                .filter(Product::isActive)
                .map(ProductView::of)
                .orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND", "No product with code " + code));
    }
}
