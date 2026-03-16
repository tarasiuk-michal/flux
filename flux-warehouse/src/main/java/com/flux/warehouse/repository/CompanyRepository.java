package com.flux.warehouse.repository;

import com.flux.warehouse.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {
    Optional<Company> findBySymbolAndMarketCode(String symbol, String marketCode);

    @Query("SELECT c FROM Company c JOIN FETCH c.market")
    List<Company> findAllWithMarket();
}
