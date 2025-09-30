package org.moqui.marketplace.matching;

import org.moqui.context.ExecutionContext;
import org.moqui.entity.EntityList;
import org.moqui.entity.EntityValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 智能匹配引擎
 * 根据标签相似度、地理位置、价格、时效性、用户偏好等多维度计算匹配分数
 */
public class SmartMatchingEngine {
    private static final Logger logger = LoggerFactory.getLogger(SmartMatchingEngine.class);

    private final ExecutionContext ec;

    // 权重配置
    private static final BigDecimal WEIGHT_TAG_SIMILARITY = new BigDecimal("0.35");
    private static final BigDecimal WEIGHT_GEO_PROXIMITY = new BigDecimal("0.25");
    private static final BigDecimal WEIGHT_PRICE_MATCH = new BigDecimal("0.15");
    private static final BigDecimal WEIGHT_FRESHNESS = new BigDecimal("0.10");
    private static final BigDecimal WEIGHT_PREFERENCE = new BigDecimal("0.15");

    public SmartMatchingEngine(ExecutionContext ec) {
        this.ec = ec;
    }

    /**
     * 为指定Listing查找匹配对象
     */
    public List<Map<String, Object>> findMatchesForListing(String listingId, int maxResults, BigDecimal minScore) {
        logger.info("Finding matches for listing: {}", listingId);

        // 1. 获取源Listing信息
        EntityValue sourceListing = ec.getEntity().find("marketplace.listing.Listing")
                .condition("listingId", listingId)
                .one();

        if (sourceListing == null) {
            logger.warn("Listing not found: {}", listingId);
            return Collections.emptyList();
        }

        String sourceType = sourceListing.getString("listingType");
        String targetType = sourceType.equals("SUPPLY") ? "DEMAND" : "SUPPLY";

        // 2. 查找候选Listing（同品类、活跃状态）
        EntityList candidates = ec.getEntity().find("marketplace.listing.Listing")
                .condition("listingType", targetType)
                .condition("status", "ACTIVE")
                .condition("category", sourceListing.getString("category"))
                .list();

        logger.info("Found {} candidate listings", candidates.size());

        // 3. 计算每个候选的匹配分数
        List<Map<String, Object>> matches = new ArrayList<>();
        for (EntityValue candidate : candidates) {
            Map<String, Object> matchResult = calculateMatchScore(sourceListing, candidate);
            BigDecimal matchScore = (BigDecimal) matchResult.get("matchScore");

            if (matchScore.compareTo(minScore) >= 0) {
                matchResult.put("candidateListing", candidate);
                matches.add(matchResult);
            }
        }

        // 4. 按分数降序排序并限制数量
        matches.sort((m1, m2) -> {
            BigDecimal score1 = (BigDecimal) m1.get("matchScore");
            BigDecimal score2 = (BigDecimal) m2.get("matchScore");
            return score2.compareTo(score1);
        });

        if (matches.size() > maxResults) {
            matches = matches.subList(0, maxResults);
        }

        logger.info("Found {} matches above threshold {}", matches.size(), minScore);
        return matches;
    }

    /**
     * 计算两个Listing之间的详细匹配分数
     */
    public Map<String, Object> calculateMatchScore(EntityValue listing1, EntityValue listing2) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 1. 标签相似度 (35%)
            BigDecimal tagSimilarity = calculateTagSimilarity(
                    listing1.getString("listingId"),
                    listing2.getString("listingId")
            );

            // 2. 地理接近度 (25%)
            BigDecimal geoProximity = calculateGeoProximity(
                    listing1.getString("geoPointId"),
                    listing2.getString("geoPointId"),
                    listing1.getBigDecimal("deliveryRange")
            );

            // 3. 价格匹配度 (15%)
            BigDecimal priceMatch = calculatePriceMatch(
                    listing1.getBigDecimal("priceMin"),
                    listing1.getBigDecimal("priceMax"),
                    listing2.getBigDecimal("priceMin"),
                    listing2.getBigDecimal("priceMax")
            );

            // 4. 时效性/新鲜度 (10%)
            BigDecimal freshnessScore = calculateFreshnessScore(
                    listing1.getTimestamp("createdDate"),
                    listing2.getTimestamp("createdDate")
            );

            // 5. 用户偏好分数 (15%)
            BigDecimal preferenceScore = calculatePreferenceScore(
                    listing1.getString("publisherId"),
                    listing2.getString("publisherId"),
                    listing2.getString("category")
            );

            // 6. 加权计算总分
            BigDecimal totalScore = tagSimilarity.multiply(WEIGHT_TAG_SIMILARITY)
                    .add(geoProximity.multiply(WEIGHT_GEO_PROXIMITY))
                    .add(priceMatch.multiply(WEIGHT_PRICE_MATCH))
                    .add(freshnessScore.multiply(WEIGHT_FRESHNESS))
                    .add(preferenceScore.multiply(WEIGHT_PREFERENCE));

            result.put("matchScore", totalScore.setScale(4, RoundingMode.HALF_UP));
            result.put("tagSimilarity", tagSimilarity);
            result.put("geoProximity", geoProximity);
            result.put("priceMatch", priceMatch);
            result.put("freshnessScore", freshnessScore);
            result.put("preferenceScore", preferenceScore);

            logger.debug("Match score calculated: {} (tag:{}, geo:{}, price:{}, fresh:{}, pref:{})",
                    totalScore, tagSimilarity, geoProximity, priceMatch, freshnessScore, preferenceScore);

        } catch (Exception e) {
            logger.error("Error calculating match score", e);
            result.put("matchScore", BigDecimal.ZERO);
        }

        return result;
    }

    /**
     * 计算标签相似度 (Jaccard相似度)
     */
    private BigDecimal calculateTagSimilarity(String listingId1, String listingId2) {
        // 获取两个Listing的标签集合
        Set<String> tags1 = getListingTags(listingId1);
        Set<String> tags2 = getListingTags(listingId2);

        if (tags1.isEmpty() || tags2.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // 计算交集
        Set<String> intersection = new HashSet<>(tags1);
        intersection.retainAll(tags2);

        // 计算并集
        Set<String> union = new HashSet<>(tags1);
        union.addAll(tags2);

        // Jaccard相似度 = |交集| / |并集|
        double similarity = (double) intersection.size() / union.size();
        return BigDecimal.valueOf(similarity).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * 获取Listing的标签集合
     */
    private Set<String> getListingTags(String listingId) {
        EntityList listingTags = ec.getEntity().find("marketplace.listing.ListingTag")
                .condition("listingId", listingId)
                .list();

        return listingTags.stream()
                .map(lt -> lt.getString("tagId"))
                .collect(Collectors.toSet());
    }

    /**
     * 计算地理接近度
     * 使用简化的距离衰减函数
     */
    private BigDecimal calculateGeoProximity(String geoPointId1, String geoPointId2, BigDecimal deliveryRange) {
        if (geoPointId1 == null || geoPointId2 == null) {
            return new BigDecimal("0.5"); // 缺少位置信息时给中等分数
        }

        try {
            // 获取地理坐标
            EntityValue geo1 = ec.getEntity().find("mantle.humanres.position.GeoPoint")
                    .condition("geoPointId", geoPointId1)
                    .one();
            EntityValue geo2 = ec.getEntity().find("mantle.humanres.position.GeoPoint")
                    .condition("geoPointId", geoPointId2)
                    .one();

            if (geo1 == null || geo2 == null) {
                return new BigDecimal("0.5");
            }

            double lat1 = geo1.getBigDecimal("latitude").doubleValue();
            double lon1 = geo1.getBigDecimal("longitude").doubleValue();
            double lat2 = geo2.getBigDecimal("latitude").doubleValue();
            double lon2 = geo2.getBigDecimal("longitude").doubleValue();

            // 计算距离(km) - 使用Haversine公式
            double distance = calculateDistance(lat1, lon1, lat2, lon2);

            // 距离衰减函数：在配送范围内得分高，超出范围快速衰减
            double maxRange = deliveryRange != null ? deliveryRange.doubleValue() : 5.0;

            double proximity;
            if (distance <= maxRange) {
                // 范围内：线性递减
                proximity = 1.0 - (distance / maxRange) * 0.5;
            } else {
                // 范围外：快速衰减
                proximity = 0.5 * Math.exp(-(distance - maxRange) / maxRange);
            }

            return BigDecimal.valueOf(proximity).setScale(4, RoundingMode.HALF_UP);

        } catch (Exception e) {
            logger.warn("Error calculating geo proximity", e);
            return new BigDecimal("0.5");
        }
    }

    /**
     * Haversine公式计算两点间距离(km)
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371; // 地球半径(km)

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }

    /**
     * 计算价格匹配度
     */
    private BigDecimal calculatePriceMatch(BigDecimal price1Min, BigDecimal price1Max,
                                           BigDecimal price2Min, BigDecimal price2Max) {
        // 如果任一方没有价格信息，返回中等分数
        if (price1Min == null || price2Min == null) {
            return new BigDecimal("0.7");
        }

        // 使用价格区间的中点
        BigDecimal price1Avg = (price1Max != null) ?
                price1Min.add(price1Max).divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP) : price1Min;
        BigDecimal price2Avg = (price2Max != null) ?
                price2Min.add(price2Max).divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP) : price2Min;

        // 计算价格差异百分比
        BigDecimal priceDiff = price1Avg.subtract(price2Avg).abs();
        BigDecimal avgPrice = price1Avg.add(price2Avg).divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
        double diffPercent = priceDiff.divide(avgPrice, 4, RoundingMode.HALF_UP).doubleValue();

        // 价格差异越小，匹配度越高
        double match = Math.exp(-diffPercent * 2); // 指数衰减
        return BigDecimal.valueOf(match).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * 计算时效性分数
     * 信息越新鲜，分数越高
     */
    private BigDecimal calculateFreshnessScore(java.sql.Timestamp created1, java.sql.Timestamp created2) {
        if (created1 == null || created2 == null) {
            return new BigDecimal("0.5");
        }

        // 计算平均发布时间到现在的小时数
        long now = System.currentTimeMillis();
        long age1 = (now - created1.getTime()) / (1000 * 60 * 60); // 小时
        long age2 = (now - created2.getTime()) / (1000 * 60 * 60);
        double avgAge = (age1 + age2) / 2.0;

        // 48小时内：高分；48小时后：指数衰减
        double freshness;
        if (avgAge <= 48) {
            freshness = 1.0 - (avgAge / 48.0) * 0.3; // 最多降低30%
        } else {
            freshness = 0.7 * Math.exp(-(avgAge - 48) / 48.0);
        }

        return BigDecimal.valueOf(freshness).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * 计算用户偏好分数
     * 基于历史交易和行为数据
     */
    private BigDecimal calculatePreferenceScore(String partyId1, String partyId2, String category) {
        try {
            // 获取用户画像
            EntityValue profile1 = ec.getEntity().find("marketplace.profile.UserProfile")
                    .condition("partyId", partyId1)
                    .one();
            EntityValue profile2 = ec.getEntity().find("marketplace.profile.UserProfile")
                    .condition("partyId", partyId2)
                    .one();

            if (profile1 == null || profile2 == null) {
                return new BigDecimal("0.5"); // 缺少画像信息
            }

            double score = 0.5; // 基础分

            // 检查品类偏好
            String preferredCategories1 = profile1.getString("preferredCategories");
            String preferredCategories2 = profile2.getString("preferredCategories");

            if (preferredCategories1 != null && preferredCategories1.contains(category)) {
                score += 0.2;
            }
            if (preferredCategories2 != null && preferredCategories2.contains(category)) {
                score += 0.2;
            }

            // 信用评分影响
            BigDecimal credit1 = profile1.getBigDecimal("creditScore");
            BigDecimal credit2 = profile2.getBigDecimal("creditScore");
            if (credit1 != null && credit2 != null) {
                double avgCredit = credit1.add(credit2).divide(new BigDecimal("2"), 4, RoundingMode.HALF_UP).doubleValue();
                score += avgCredit * 0.1; // 信用分贡献最多10%
            }

            return BigDecimal.valueOf(Math.min(score, 1.0)).setScale(4, RoundingMode.HALF_UP);

        } catch (Exception e) {
            logger.warn("Error calculating preference score", e);
            return new BigDecimal("0.5");
        }
    }

    /**
     * 生成匹配推荐理由
     * 基于匹配分数的各个维度
     */
    public String generateMatchReason(Map<String, Object> matchScores, EntityValue supplyListing, EntityValue demandListing) {
        StringBuilder reason = new StringBuilder();

        BigDecimal tagSim = (BigDecimal) matchScores.get("tagSimilarity");
        BigDecimal geoProx = (BigDecimal) matchScores.get("geoProximity");
        BigDecimal priceMatch = (BigDecimal) matchScores.get("priceMatch");

        // 主要推荐理由（最高分的维度）
        if (tagSim.compareTo(new BigDecimal("0.7")) >= 0) {
            reason.append("商品品类高度匹配；");
        }

        if (geoProx.compareTo(new BigDecimal("0.8")) >= 0) {
            reason.append("距离很近，配送方便；");
        } else if (geoProx.compareTo(new BigDecimal("0.5")) >= 0) {
            reason.append("位置在可配送范围内；");
        }

        if (priceMatch.compareTo(new BigDecimal("0.8")) >= 0) {
            reason.append("价格非常合适；");
        }

        // 添加信用信息
        EntityValue publisherProfile = ec.getEntity().find("marketplace.profile.UserProfile")
                .condition("partyId", supplyListing.getString("publisherId"))
                .one();

        if (publisherProfile != null) {
            BigDecimal creditScore = publisherProfile.getBigDecimal("creditScore");
            if (creditScore != null && creditScore.compareTo(new BigDecimal("0.8")) >= 0) {
                reason.append("商家信用良好；");
            }

            Integer totalOrders = publisherProfile.getInteger("totalOrders");
            if (totalOrders != null && totalOrders > 5) {
                reason.append(String.format("已完成%d笔交易", totalOrders));
            }
        }

        return reason.length() > 0 ? reason.toString() : "综合评估推荐";
    }
}