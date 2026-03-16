package com.flux.warehouse.service;

import com.flux.warehouse.dto.DataDTO;
import com.flux.warehouse.repository.MarketRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class QueryService {

    private final JdbcTemplate jdbcTemplate;
    private final MarketRepository marketRepository;

    public QueryService(JdbcTemplate jdbcTemplate, MarketRepository marketRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.marketRepository = marketRepository;
    }

    public List<DataDTO> queryPrices(String market, String symbol, Integer limit) {
        // Validate market exists
        if (marketRepository.findByCode(market).isEmpty()) {
            throw new IllegalArgumentException("Unknown market: " + market);
        }

        int queryLimit = limit != null ? limit : 100;

        String sql;
        List<Object> params = new ArrayList<>();

        if (symbol != null && !symbol.isEmpty()) {
            sql = "SELECT p.price, p.volume, p.timestamp, c.symbol, m.code AS market " +
                  "FROM prices p " +
                  "JOIN company c ON p.company_id = c.id " +
                  "JOIN market m ON c.market_id = m.id " +
                  "WHERE m.code = ? AND c.symbol = ? " +
                  "ORDER BY p.id DESC LIMIT ?";
            params.add(market);
            params.add(symbol);
            params.add(queryLimit);
        } else {
            sql = "SELECT p.price, p.volume, p.timestamp, c.symbol, m.code AS market " +
                  "FROM prices p " +
                  "JOIN company c ON p.company_id = c.id " +
                  "JOIN market m ON c.market_id = m.id " +
                  "WHERE m.code = ? " +
                  "ORDER BY p.id DESC LIMIT ?";
            params.add(market);
            params.add(queryLimit);
        }

        return jdbcTemplate.query(sql, params.toArray(), (rs, rowNum) -> new DataDTO(
            rs.getString("symbol"),
            rs.getString("market"),
            rs.getDouble("price"),
            rs.getLong("volume"),
            rs.getString("timestamp")
        ));
    }
}
