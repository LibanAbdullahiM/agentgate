package fi.agentgate.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, String> {

    /** Patterns are built in Java ("%text%" or "%") so PostgreSQL never sees an untyped null. */
    @Query("""
            select p from Product p
            where p.active = true
              and (lower(p.name) like :pattern or lower(p.code) like :pattern)
              and coalesce(lower(p.category), '') like :categoryPattern
            """)
    Page<Product> search(@Param("pattern") String pattern,
                         @Param("categoryPattern") String categoryPattern,
                         Pageable pageable);
}
