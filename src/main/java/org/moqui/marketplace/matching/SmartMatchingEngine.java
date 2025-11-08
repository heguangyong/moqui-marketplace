package org.moqui.marketplace.matching;

import org.moqui.context.ExecutionContext;
import org.moqui.entity.EntityList;
import org.moqui.entity.EntityValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import groovy.json.JsonSlurper;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 智能匹配引擎
 * 根据标签相似度、地理位置、价格、时效性、用户偏好等多维度计算匹配分数
 */
public class SmartMatchingEngine {
    private static final Logger logger = LoggerFactory.getLogger(SmartMatchingEngine.class);

    private final ExecutionContext ec;

    // 默认权重配置
    private static final BigDecimal DEFAULT_WEIGHT_TAG_SIMILARITY = new BigDecimal("0.30");
    private static final BigDecimal DEFAULT_WEIGHT_GEO_PROXIMITY = new BigDecimal("0.20");
    private static final BigDecimal DEFAULT_WEIGHT_PRICE_MATCH = new BigDecimal("0.15");
    private static final BigDecimal DEFAULT_WEIGHT_FRESHNESS = new BigDecimal("0.10");
    private static final BigDecimal DEFAULT_WEIGHT_PREFERENCE = new BigDecimal("0.10");
    private static final BigDecimal DEFAULT_WEIGHT_PROJECT_AFFINITY = new BigDecimal("0.15");

    private static final BigDecimal DEFAULT_MIN_SCORE = new BigDecimal("0.6");
    private static final BigDecimal DEFAULT_GEO_FALLBACK_SCORE = new BigDecimal("0.5");

    private static final String MATCHING_CONFIG_PROPERTY = "marketplace.matching.config.location";
    private static final String MATCHING_CONFIG_DEFAULT_LOCATION = "component://moqui-marketplace/config/matching-config.json";
    private static final long CONFIG_CACHE_TTL_MS = 5 * 60 * 1000L;
    private static final Object CONFIG_LOCK = new Object();
    private static Map<String, Object> cachedConfig;
    private static long configLoadedTs = 0L;

    private static final List<String> DEFAULT_EXHIBITION_KEYWORDS = Arrays.asList("展台", "搭建", "会展", "展览", "布展", "展位", "展厅", "展馆", "巡展");
    private static final List<String> DEFAULT_RENOVATION_KEYWORDS = Arrays.asList("装修", "改造", "翻新", "设计", "施工", "家装", "工装", "装潢", "软装", "硬装");
    private static final List<String> DEFAULT_ENGINEERING_KEYWORDS = Arrays.asList("工程", "总包", "施工队", "钢结构", "机电", "土建", "建材", "脚手架", "设备租赁", "电气", "管道", "消防", "弱电", "暖通", "安装");
    private static final List<String> DEFAULT_STYLE_KEYWORDS = Arrays.asList("现代", "科技", "工业", "中式", "欧式", "简约", "奢华", "北欧", "复古", "工业风", "极简", "科技感");
    private static final List<String> DEFAULT_MATERIAL_KEYWORDS = Arrays.asList("钢结构", "桁架", "木材", "灯光", "音响", "LED", "玻璃", "铝合金", "地毯", "石材", "PVC", "喷绘", "舞台", "幕布", "地板", "龙骨", "设备");

    private BigDecimal weightTagSimilarity = DEFAULT_WEIGHT_TAG_SIMILARITY;
    private BigDecimal weightGeoProximity = DEFAULT_WEIGHT_GEO_PROXIMITY;
    private BigDecimal weightPriceMatch = DEFAULT_WEIGHT_PRICE_MATCH;
    private BigDecimal weightFreshness = DEFAULT_WEIGHT_FRESHNESS;
    private BigDecimal weightPreference = DEFAULT_WEIGHT_PREFERENCE;
    private BigDecimal weightProjectAffinity = DEFAULT_WEIGHT_PROJECT_AFFINITY;
    private BigDecimal geoFallbackScore = DEFAULT_GEO_FALLBACK_SCORE;

    private List<String> exhibitionKeywords = new ArrayList<>(DEFAULT_EXHIBITION_KEYWORDS);
    private List<String> renovationKeywords = new ArrayList<>(DEFAULT_RENOVATION_KEYWORDS);
    private List<String> engineeringKeywords = new ArrayList<>(DEFAULT_ENGINEERING_KEYWORDS);
    private List<String> styleKeywords = new ArrayList<>(DEFAULT_STYLE_KEYWORDS);
    private List<String> materialKeywords = new ArrayList<>(DEFAULT_MATERIAL_KEYWORDS);

    public SmartMatchingEngine(ExecutionContext ec) {
        this.ec = ec;
        loadRuntimeConfig();
    }

    public static void clearCachedConfig() {
        synchronized (CONFIG_LOCK) {
            cachedConfig = null;
            configLoadedTs = 0L;
        }
    }

    public static BigDecimal getConfiguredDefaultMinScore(ExecutionContext ec) {
        Map<String, Object> config = getMatchingConfig(ec);
        if (config != null) {
            Object thresholdsObj = config.get("thresholds");
            if (thresholdsObj instanceof Map) {
                Object value = ((Map<?, ?>) thresholdsObj).get("defaultMinScore");
                BigDecimal parsed = toBigDecimal(value, null);
                if (parsed != null) return parsed;
            }
        }
        return DEFAULT_MIN_SCORE;
    }

    private void loadRuntimeConfig() {
        resetToDefaults();
        Map<String, Object> config = getMatchingConfig(ec);
        if (config == null) return;

        Object weightsObj = config.get("weights");
        if (weightsObj instanceof Map) {
            Map<?, ?> weights = (Map<?, ?>) weightsObj;
            weightTagSimilarity = toBigDecimal(weights.get("tagSimilarity"), DEFAULT_WEIGHT_TAG_SIMILARITY);
            weightGeoProximity = toBigDecimal(weights.get("geoProximity"), DEFAULT_WEIGHT_GEO_PROXIMITY);
            weightPriceMatch = toBigDecimal(weights.get("priceMatch"), DEFAULT_WEIGHT_PRICE_MATCH);
            weightFreshness = toBigDecimal(weights.get("freshness"), DEFAULT_WEIGHT_FRESHNESS);
            weightPreference = toBigDecimal(weights.get("preference"), DEFAULT_WEIGHT_PREFERENCE);
            weightProjectAffinity = toBigDecimal(weights.get("projectAffinity"), DEFAULT_WEIGHT_PROJECT_AFFINITY);
        }

        Object thresholdsObj = config.get("thresholds");
        if (thresholdsObj instanceof Map) {
            Map<?, ?> thresholds = (Map<?, ?>) thresholdsObj;
            geoFallbackScore = toBigDecimal(thresholds.get("geoFallbackScore"), DEFAULT_GEO_FALLBACK_SCORE);
        }

        Object keywordsObj = config.get("keywords");
        if (keywordsObj instanceof Map) {
            Map<?, ?> keywords = (Map<?, ?>) keywordsObj;
            exhibitionKeywords = toStringList(keywords.get("exhibition"), DEFAULT_EXHIBITION_KEYWORDS);
            renovationKeywords = toStringList(keywords.get("renovation"), DEFAULT_RENOVATION_KEYWORDS);
            engineeringKeywords = toStringList(keywords.get("engineering"), DEFAULT_ENGINEERING_KEYWORDS);
            styleKeywords = toStringList(keywords.get("style"), DEFAULT_STYLE_KEYWORDS);
            materialKeywords = toStringList(keywords.get("material"), DEFAULT_MATERIAL_KEYWORDS);
        }
    }

    private void resetToDefaults() {
        weightTagSimilarity = DEFAULT_WEIGHT_TAG_SIMILARITY;
        weightGeoProximity = DEFAULT_WEIGHT_GEO_PROXIMITY;
        weightPriceMatch = DEFAULT_WEIGHT_PRICE_MATCH;
        weightFreshness = DEFAULT_WEIGHT_FRESHNESS;
        weightPreference = DEFAULT_WEIGHT_PREFERENCE;
        weightProjectAffinity = DEFAULT_WEIGHT_PROJECT_AFFINITY;
        geoFallbackScore = DEFAULT_GEO_FALLBACK_SCORE;

        exhibitionKeywords = new ArrayList<>(DEFAULT_EXHIBITION_KEYWORDS);
        renovationKeywords = new ArrayList<>(DEFAULT_RENOVATION_KEYWORDS);
        engineeringKeywords = new ArrayList<>(DEFAULT_ENGINEERING_KEYWORDS);
        styleKeywords = new ArrayList<>(DEFAULT_STYLE_KEYWORDS);
        materialKeywords = new ArrayList<>(DEFAULT_MATERIAL_KEYWORDS);
    }

    private static Map<String, Object> getMatchingConfig(ExecutionContext ec) {
        long now = System.currentTimeMillis();
        synchronized (CONFIG_LOCK) {
            if (cachedConfig != null && (now - configLoadedTs) < CONFIG_CACHE_TTL_MS) {
                return cachedConfig;
            }
            String location = System.getProperty(MATCHING_CONFIG_PROPERTY);
            if (location == null || location.isEmpty()) {
                String envKey = MATCHING_CONFIG_PROPERTY.toUpperCase().replace('.', '_');
                location = System.getenv(envKey);
            }
            if (location == null || location.isEmpty()) {
                location = MATCHING_CONFIG_DEFAULT_LOCATION;
            }
            try {
                String configText = ec.getResource().getLocationText(location, false);
                if (configText != null && !configText.trim().isEmpty()) {
                    Object parsed = new JsonSlurper().parseText(configText);
                    if (parsed instanceof Map) {
                        cachedConfig = (Map<String, Object>) parsed;
                        configLoadedTs = now;
                        return cachedConfig;
                    }
                }
            } catch (Exception e) {
                logger.warn("Unable to load matching config from {}: {}", location, e.getMessage());
            }
            cachedConfig = null;
            configLoadedTs = now;
            return null;
        }
    }

    private static BigDecimal toBigDecimal(Object value, BigDecimal defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        try {
            return new BigDecimal(value.toString());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static List<String> toStringList(Object value, List<String> defaults) {
        if (value instanceof Collection) {
            List<String> result = new ArrayList<>();
            for (Object obj : (Collection<?>) value) {
                if (obj != null) {
                    String str = obj.toString().trim();
                    if (!str.isEmpty()) result.add(str);
                }
            }
            if (!result.isEmpty()) return result;
        }
        return new ArrayList<>(defaults);
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
        ProjectProfile sourceProfile = extractProjectProfile(sourceListing);

        List<Map<String, Object>> matches = new ArrayList<>();
        for (EntityValue candidate : candidates) {
            ProjectProfile candidateProfile = extractProjectProfile(candidate);
            Map<String, Object> matchResult = calculateMatchScore(sourceListing, candidate, sourceProfile, candidateProfile);
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
        return calculateMatchScore(listing1, listing2, null, null);
    }

    public Map<String, Object> calculateMatchScore(EntityValue listing1, EntityValue listing2,
                                                   ProjectProfile profile1, ProjectProfile profile2) {
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

            ProjectProfile effectiveProfile1 = profile1 != null ? profile1 : extractProjectProfile(listing1);
            ProjectProfile effectiveProfile2 = profile2 != null ? profile2 : extractProjectProfile(listing2);
            BigDecimal projectAffinity = calculateProjectAffinity(effectiveProfile1, effectiveProfile2);

            // 6. 加权计算总分
            BigDecimal totalScore = tagSimilarity.multiply(weightTagSimilarity)
                    .add(geoProximity.multiply(weightGeoProximity))
                    .add(priceMatch.multiply(weightPriceMatch))
                    .add(freshnessScore.multiply(weightFreshness))
                    .add(preferenceScore.multiply(weightPreference))
                    .add(projectAffinity.multiply(weightProjectAffinity));

            result.put("matchScore", totalScore.setScale(4, RoundingMode.HALF_UP));
            result.put("tagSimilarity", tagSimilarity);
            result.put("geoProximity", geoProximity);
            result.put("priceMatch", priceMatch);
            result.put("freshnessScore", freshnessScore);
            result.put("preferenceScore", preferenceScore);
            result.put("projectAffinity", projectAffinity);

            logger.debug("Match score calculated: {} (tag:{}, geo:{}, price:{}, fresh:{}, pref:{}, project:{})",
                    totalScore, tagSimilarity, geoProximity, priceMatch, freshnessScore, preferenceScore, projectAffinity);

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
            return geoFallbackScore; // 缺少位置信息时给中等分数
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
                return geoFallbackScore;
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
            return geoFallbackScore;
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

    private ProjectProfile extractProjectProfile(EntityValue listing) {
        ProjectProfile profile = new ProjectProfile();
        if (listing == null) {
            return profile;
        }

        StringBuilder rawBuilder = new StringBuilder();
        String title = listing.getString("title");
        if (title != null) rawBuilder.append(title).append(" ");
        String description = listing.getString("description");
        if (description != null) rawBuilder.append(description).append(" ");
        String category = listing.getString("category");
        if (category != null) rawBuilder.append(category).append(" ");
        String subCategory = listing.getString("subCategory");
        if (subCategory != null) rawBuilder.append(subCategory).append(" ");

        try {
            EntityList insights = ec.getEntity().find("marketplace.listing.ListingInsight")
                    .condition("listingId", listing.getString("listingId"))
                    .list();
            for (EntityValue insight : insights) {
                String summary = insight.getString("summary");
                if (summary != null) rawBuilder.append(summary).append(" ");
                String metadataJson = insight.getString("metadataJson");
                if (metadataJson != null && !metadataJson.isEmpty()) {
                    try {
                        Object metadataObj = new JsonSlurper().parseText(metadataJson);
                        if (metadataObj instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> metadataMap = (Map<String, Object>) metadataObj;
                            profile.metadata.putAll(metadataMap);
                            for (Object value : metadataMap.values()) {
                                if (value != null) rawBuilder.append(value.toString()).append(" ");
                            }
                        }
                    } catch (Exception parseEx) {
                        logger.debug("Failed to parse metadataJson for listing {}: {}", listing.getString("listingId"), parseEx.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to load listing insights for {}: {}", listing.getString("listingId"), e.getMessage());
        }

        String rawText = rawBuilder.toString();
        if (rawText.isEmpty()) {
            return profile;
        }

        profile.keywords.addAll(extractChineseTokens(rawText));

        int exhibitionCount = countKeywordMatches(rawText, exhibitionKeywords);
        int renovationCount = countKeywordMatches(rawText, renovationKeywords);
        int engineeringCount = countKeywordMatches(rawText, engineeringKeywords);

        if (exhibitionCount >= renovationCount && exhibitionCount >= engineeringCount && exhibitionCount > 0) {
            profile.projectType = "EXHIBITION_SETUP";
        } else if (renovationCount >= exhibitionCount && renovationCount >= engineeringCount && renovationCount > 0) {
            profile.projectType = "RENOVATION";
        } else if (engineeringCount > 0) {
            profile.projectType = "ENGINEERING";
        }

        Object metadataProjectType = profile.metadata.get("projectType");
        if (metadataProjectType instanceof String && !((String) metadataProjectType).isEmpty()) {
            profile.projectType = (String) metadataProjectType;
        }

        Matcher areaMatcher = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(平米|平方米|㎡|m2|平方)").matcher(rawText);
        if (areaMatcher.find()) {
            profile.areaSquare = Double.valueOf(areaMatcher.group(1));
        } else if (profile.metadata.get("estimatedArea") instanceof Number) {
            profile.areaSquare = ((Number) profile.metadata.get("estimatedArea")).doubleValue();
        }

        Matcher budgetMatcher = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(万|万元|千|k|元|人民币|rmb)").matcher(rawText.toLowerCase());
        if (budgetMatcher.find()) {
            profile.budgetAmount = convertBudget(Double.valueOf(budgetMatcher.group(1)), budgetMatcher.group(2));
        } else if (profile.metadata.get("budgetAmountCny") instanceof Number) {
            profile.budgetAmount = ((Number) profile.metadata.get("budgetAmountCny")).doubleValue();
        }

        Matcher durationMatcher = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(天|日|周|月|年)").matcher(rawText);
        if (durationMatcher.find()) {
            profile.durationDays = convertDuration(Double.valueOf(durationMatcher.group(1)), durationMatcher.group(2));
        } else if (profile.metadata.get("estimatedDurationDays") instanceof Number) {
            profile.durationDays = ((Number) profile.metadata.get("estimatedDurationDays")).doubleValue();
        }

        Matcher locationMatcher = Pattern.compile("(?:在|位于|地址|地点|于)([\\p{IsHan}]{2,9})(?:省|市|区|县|镇|馆|中心|展馆|工地)").matcher(rawText);
        if (locationMatcher.find()) {
            profile.locationHint = locationMatcher.group(1);
        } else if (profile.metadata.get("locationHints") instanceof Collection) {
            Collection<?> hints = (Collection<?>) profile.metadata.get("locationHints");
            if (!hints.isEmpty()) {
                profile.locationHint = hints.iterator().next().toString();
            }
        }

        for (String style : styleKeywords) {
            if (rawText.contains(style)) {
                profile.styleTags.add(style);
            }
        }
        if (profile.metadata.get("stylePreferences") instanceof Collection) {
            Collection<?> styles = (Collection<?>) profile.metadata.get("stylePreferences");
            for (Object style : styles) {
                if (style != null) profile.styleTags.add(style.toString());
            }
        }

        for (String material : materialKeywords) {
            if (rawText.contains(material)) {
                profile.materialTags.add(material);
            }
        }
        if (profile.metadata.get("materialKeywords") instanceof Collection) {
            Collection<?> materials = (Collection<?>) profile.metadata.get("materialKeywords");
            for (Object material : materials) {
                if (material != null) profile.materialTags.add(material.toString());
            }
        }

        return profile;
    }

    private BigDecimal calculateProjectAffinity(ProjectProfile profile1, ProjectProfile profile2) {
        if (profile1 == null || profile2 == null) {
            return new BigDecimal("0.5");
        }
        if (!profile1.isProject() && !profile2.isProject()) {
            return new BigDecimal("0.5");
        }

        double score = 0.5;

        if (profile1.projectType != null && profile2.projectType != null) {
            if (profile1.projectType.equals(profile2.projectType)) {
                score = 0.75;
            } else if (profile1.isProject() && profile2.isProject()) {
                score = 0.4;
            }
        } else if (profile1.isProject() || profile2.isProject()) {
            score = 0.55;
        }

        if (profile1.areaSquare != null && profile2.areaSquare != null) {
            double smaller = Math.min(profile1.areaSquare, profile2.areaSquare);
            double larger = Math.max(profile1.areaSquare, profile2.areaSquare);
            if (larger > 0) {
                double ratio = smaller / larger;
                if (ratio >= 0.9) score += 0.1;
                else if (ratio >= 0.75) score += 0.07;
                else if (ratio >= 0.6) score += 0.04;
                else score -= 0.05;
            }
        }

        if (profile1.budgetAmount != null && profile2.budgetAmount != null) {
            double diff = Math.abs(profile1.budgetAmount - profile2.budgetAmount);
            double avg = (profile1.budgetAmount + profile2.budgetAmount) / 2.0;
            if (avg > 0) {
                double diffRatio = diff / avg;
                if (diffRatio <= 0.2) score += 0.08;
                else if (diffRatio <= 0.35) score += 0.05;
                else if (diffRatio <= 0.5) score += 0.02;
                else score -= 0.05;
            }
        }

        if (profile1.durationDays != null && profile2.durationDays != null) {
            double diff = Math.abs(profile1.durationDays - profile2.durationDays);
            if (diff <= 7) score += 0.04;
            else if (diff <= 14) score += 0.02;
            else score -= 0.03;
        }

        if (profile1.locationHint != null && profile2.locationHint != null) {
            if (profile1.locationHint.equals(profile2.locationHint)) {
                score += 0.08;
            } else if (profile1.locationHint.startsWith(profile2.locationHint)
                    || profile2.locationHint.startsWith(profile1.locationHint)) {
                score += 0.04;
            } else {
                score -= 0.04;
            }
        }

        Set<String> styleIntersection = new HashSet<>(profile1.styleTags);
        styleIntersection.retainAll(profile2.styleTags);
        if (!styleIntersection.isEmpty()) {
            score += 0.03;
        }

        Set<String> materialIntersection = new HashSet<>(profile1.materialTags);
        materialIntersection.retainAll(profile2.materialTags);
        if (!materialIntersection.isEmpty()) {
            score += 0.02;
        }

        score = Math.max(0.0, Math.min(1.0, score));
        return BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP);
    }

    private int countKeywordMatches(String text, List<String> keywords) {
        if (text == null || text.isEmpty()) return 0;
        String lower = text.toLowerCase();
        int count = 0;
        for (String keyword : keywords) {
            if (text.contains(keyword) || lower.contains(keyword.toLowerCase())) {
                count++;
            }
        }
        return count;
    }

    private Set<String> extractChineseTokens(String text) {
        Set<String> tokens = new HashSet<>();
        Matcher matcher = Pattern.compile("\\p{IsHan}+").matcher(text);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    private Double convertBudget(Double value, String unit) {
        if (value == null || unit == null) return value;
        String lower = unit.toLowerCase();
        if (lower.contains("万")) {
            return value * 10000d;
        } else if (lower.contains("千") || lower.contains("k")) {
            return value * 1000d;
        }
        return value;
    }

    private Double convertDuration(Double value, String unit) {
        if (value == null || unit == null) return value;
        switch (unit) {
            case "天":
            case "日":
                return value;
            case "周":
                return value * 7d;
            case "月":
                return value * 30d;
            case "年":
                return value * 365d;
            default:
                return value;
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
        BigDecimal projectAffinity = (BigDecimal) matchScores.get("projectAffinity");

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

        if (projectAffinity != null && projectAffinity.compareTo(new BigDecimal("0.6")) >= 0) {
            reason.append("项目需求与资源能力高度吻合；");
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

            Long totalOrdersLong = publisherProfile.getLong("totalOrders");
            if (totalOrdersLong != null && totalOrdersLong > 5) {
                reason.append(String.format("已完成%d笔交易", totalOrdersLong));
            }
        }

        return reason.length() > 0 ? reason.toString() : "综合评估推荐";
    }

    private static class ProjectProfile {
        String projectType = "NONE";
        Double areaSquare;
        Double budgetAmount;
        Double durationDays;
        String locationHint;
        Set<String> styleTags = new HashSet<>();
        Set<String> materialTags = new HashSet<>();
        Set<String> keywords = new HashSet<>();
        Map<String, Object> metadata = new HashMap<>();

        boolean isProject() {
            return projectType != null && !"NONE".equals(projectType) && !"NOT_PROJECT".equals(projectType);
        }
    }
}
