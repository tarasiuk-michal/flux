package com.flux.warehouse.service;

import com.flux.warehouse.dto.DataDTO;
import com.flux.warehouse.model.Market;
import com.flux.warehouse.repository.MarketRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class QueryService {

    private final JdbcTemplate jdbcTemplate;
    private final MarketRepository marketRepository;
    private Set<String> validMarketCodes;

    public QueryService(JdbcTemplate jdbcTemplate, MarketRepository marketRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.marketRepository = marketRepository;
    }

    @PostConstruct
    void initMarketCodes() {
        validMarketCodes = marketRepository.findAll().stream()
            .map(Market::getCode)
            .collect(Collectors.toSet());
    }

    public List<DataDTO> queryPrices(String market, String symbol, Integer limit) {
        if (!validMarketCodes.contains(market)) {
            throw new IllegalArgumentException("Unknown market: " + market);
        }

        int queryLimit = limit != null ? limit : 100;

        StringBuilder sql = new StringBuilder(
            "SELECT p.price, p.volume, p.timestamp, c.symbol, m.code AS market " +
            "FROM prices p " +
            "JOIN company c ON p.company_id = c.id " +
            "JOIN market m ON c.market_id = m.id " +
            "WHERE m.code = ?");

        List<Object> params = new ArrayList<>();
        params.add(market);

        if (symbol != null && !symbol.isEmpty()) {
            sql.append(" AND c.symbol = ?");
            params.add(symbol);
        }

        sql.append(" ORDER BY p.id DESC LIMIT ?");
        params.add(queryLimit);

        return jdbcTemplate.query(sql.toString(), params.toArray(), (rs, rowNum) -> new DataDTO(
            rs.getString("symbol"),
            rs.getString("market"),
            rs.getDouble("price"),
            rs.getLong("volume"),
            rs.getString("timestamp")
        ));
    }
}
