package fi.agentgate.catalog;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fi.agentgate.common.ApiException;

/**
 * Imports Valio's product JSON (a top-level array) into the local product table, upsert by product id.
 *
 * <p>Real structure (see valio_aimo_junction26_product_data_schema.json): id, salesUnit, vendorName, category
 * (a numeric group code, e.g. "17301") and allowedLotSize are top-level. The product NAMES are NOT top-level:
 * they live in synkkaData.names[{value,language}]. synkkaData fields may be missing for some products.
 *
 * <p>Allergens come from synkkaData.classifications, groups "allergen" and "nonAllergen", whose values are
 * {id: GS1 code, unit: CONTAINS | MAY_CONTAIN | FREE_FROM}. Products without such data are stored as allergens
 * "unknown" (never "none"), and a code missing from all three lists stays unknown too.
 */
@Service
public class ProductImportService {

    public record ImportResult(int imported, int skipped, String file) {
    }

    private final ProductRepository products;
    private final ObjectMapper mapper;
    private final String productFile;

    public ProductImportService(ProductRepository products, ObjectMapper mapper,
                                @Value("${valio.product-file}") String productFile) {
        this.products = products;
        this.mapper = mapper;
        this.productFile = productFile;
    }

    @Transactional
    public ImportResult importFromFile() {
        Path path = Path.of(productFile);
        if (!Files.isReadable(path)) {
            throw ApiException.notFound("PRODUCT_FILE_NOT_FOUND",
                    "Product file not found at " + productFile + ". Download it from the Valio portal into ./data/");
        }
        JsonNode root;
        try {
            root = mapper.readTree(path.toFile());
        } catch (IOException e) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PRODUCT_FILE_INVALID",
                    "Product file is not valid JSON: " + e.getMessage());
        }
        if (!root.isArray()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PRODUCT_FILE_INVALID",
                    "Expected a JSON array of products");
        }
        int imported = 0;
        int skipped = 0;
        for (JsonNode node : root) {
            Product p = map(node);
            if (p == null) {
                skipped++;
                continue;
            }
            products.save(p);
            imported++;
        }
        return new ImportResult(imported, skipped, productFile);
    }

    private Product map(JsonNode node) {
        String code = text(node, "id");
        if (code == null) {
            return null;
        }
        if (node.path("deleted").asBoolean(false)) {
            // keep history but make sure a deleted product can no longer be ordered
            products.findById(code).ifPresent(existing -> {
                existing.setActive(false);
                products.save(existing);
            });
            return null;
        }
        // The name is optional in Valio's data ("most synkkaData fields can be missing"). A product without a name
        // can still be priced and ordered, so it is kept under a neutral label instead of being dropped.
        String name = localized(node.path("synkkaData").path("names"), "en", "fi", "sv");
        if (name == null) {
            name = "Product " + code;
        }
        Product p = products.findById(code).orElseGet(Product::new);
        p.setCode(code);
        p.setName(truncate(name, 255));
        p.setCategory(truncate(text(node, "category"), 120));
        String unit = text(node, "salesUnit");
        p.setUnit(unit == null ? "pcs" : truncate(unit, 20));
        p.setSupplier(truncate(text(node, "vendorName"), 255));
        p.setLotSize(decimal(node.path("allowedLotSize")));
        p.setActive(true);
        mapAllergens(node.path("synkkaData").path("classifications"), p);
        return p;
    }

    private static void mapAllergens(JsonNode classifications, Product p) {
        Set<String> contains = new LinkedHashSet<>();
        Set<String> mayContain = new LinkedHashSet<>();
        Set<String> freeFrom = new LinkedHashSet<>();
        if (classifications.isArray()) {
            for (JsonNode group : classifications) {
                String groupName = group.path("name").asText();
                if (!groupName.equals("allergen") && !groupName.equals("nonAllergen")) {
                    continue;
                }
                for (JsonNode v : group.path("values")) {
                    String id = text(v, "id");
                    String level = text(v, "unit");
                    if (id == null || level == null) {
                        continue;
                    }
                    switch (level) {
                        case "CONTAINS" -> contains.add(id);
                        case "MAY_CONTAIN" -> mayContain.add(id);
                        case "FREE_FROM" -> freeFrom.add(id);
                        default -> {
                            // an unrecognised level is ignored: that code then stays UNKNOWN, which blocks safe mode
                        }
                    }
                }
            }
        }
        // Contradicting data (both "contains" and "free from") is resolved to the cautious side.
        freeFrom.removeAll(contains);
        freeFrom.removeAll(mayContain);
        boolean known = !(contains.isEmpty() && mayContain.isEmpty() && freeFrom.isEmpty());
        p.setAllergensKnown(known);
        p.setAllergens(known ? String.join(",", contains) : null);
        p.setAllergensMayContain(known ? String.join(",", mayContain) : null);
        p.setAllergensFreeFrom(known ? String.join(",", freeFrom) : null);
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private static BigDecimal decimal(JsonNode n) {
        return n != null && n.isNumber() ? n.decimalValue() : null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        String s = v.asText();
        return s.isBlank() ? null : s;
    }

    /** Picks the first non-blank {value, language} entry in the preferred language order, else any entry. */
    private static String localized(JsonNode array, String... preferredLanguages) {
        if (!array.isArray()) {
            return null;
        }
        for (String lang : preferredLanguages) {
            for (JsonNode entry : array) {
                if (lang.equals(entry.path("language").asText())) {
                    String v = text(entry, "value");
                    if (v != null) {
                        return v;
                    }
                }
            }
        }
        for (JsonNode entry : array) {
            String v = text(entry, "value");
            if (v != null) {
                return v;
            }
        }
        return null;
    }
}
