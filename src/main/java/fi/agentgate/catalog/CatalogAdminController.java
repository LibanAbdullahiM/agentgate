package fi.agentgate.catalog;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/catalog")
@Tag(name = "Admin")
@SecurityRequirement(name = "bearerAuth")
public class CatalogAdminController {

    private final ProductImportService importer;

    public CatalogAdminController(ProductImportService importer) {
        this.importer = importer;
    }

    /** Loads the Valio product JSON from VALIO_PRODUCT_FILE (default ./data/products.json). */
    @PostMapping("/import")
    @PreAuthorize("hasRole('BUYER_ADMIN')")
    public ProductImportService.ImportResult importProducts() {
        return importer.importFromFile();
    }
}
