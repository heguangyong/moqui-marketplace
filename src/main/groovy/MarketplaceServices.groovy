import org.moqui.context.ExecutionContext
import org.moqui.entity.EntityValue
import org.moqui.entity.EntityList
import org.moqui.entity.EntityCondition
import org.moqui.marketplace.matching.SmartMatchingEngine
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.math.BigDecimal

/**
 * Marketplace服务实现
 * 提供供需撮合、智能匹配、用户画像等核心功能
 */

// 创建供需信息
Map createListing() {
    ExecutionContext ec = context.ec
    Logger logger = LoggerFactory.getLogger("MarketplaceServices")

    logger.info("Creating listing with type: ${listingType}")

    // 验证必填字段
    if (!publisherId || !title || !category) {
        ec.message.addError("缺少必填字段：publisherId, title, category")
        return [:]
    }

    // 生成listingId
    String listingId = "LISTING_" + System.currentTimeMillis()

    // 设置默认值
    String status = "ACTIVE"
    if (!validFrom) validFrom = ec.user.nowTimestamp
    if (!validThru) {
        // 默认48小时有效期
        Integer expireHours = (ec.factory.getToolFactory("ResourceFacade")
            .getLocationReference("component://moqui-marketplace/MoquiConf.xml")
            .getText()?.contains("marketplace.listing.expire.hours")) ?
            Integer.valueOf(ec.factory.confXmlRoot."default-property".find {
                it.@name == "marketplace.listing.expire.hours"
            }?.@value ?: "48") : 48
        validThru = new Timestamp(validFrom.time + (expireHours * 60 * 60 * 1000))
    }

    // 创建Listing记录
    EntityValue listing = ec.entity.makeValue("marketplace.listing.Listing")
    listing.setAll([
        listingId: listingId,
        listingType: listingType,
        publisherId: publisherId,
        publisherType: publisherType,
        title: title,
        description: description,
        category: category,
        subCategory: subCategory,
        quantity: quantity,
        quantityUnit: quantityUnit ?: "份",
        priceMin: priceMin,
        priceMax: priceMax,
        locationDesc: locationDesc,
        geoPointId: geoPointId,
        deliveryRange: deliveryRange ?: 5.0,
        validFrom: validFrom,
        validThru: validThru,
        status: status,
        imageUrls: imageUrls
    ])
    listing.create()

    // 自动提取标签（如果启用AI）
    Boolean enableTagExtraction = "true".equals(ec.factory.getToolFactory("ResourceFacade")
        .getLocationReference("component://moqui-marketplace/MoquiConf.xml")
        .getText()?.contains("marketplace.ai.enable.tag.extraction") ?
        ec.factory.confXmlRoot."default-property".find {
            it.@name == "marketplace.ai.enable.tag.extraction"
        }?.@value : "false")

    if (enableTagExtraction && description) {
        // TODO: 调用AI服务提取标签
        logger.info("AI标签提取功能尚未实现")
    }

    // 自动寻找匹配
    try {
        SmartMatchingEngine engine = new SmartMatchingEngine(ec)
        List matches = engine.findMatchesForListing(listingId, 5, new BigDecimal("0.6"))
        logger.info("Found ${matches.size()} potential matches for listing ${listingId}")

        // 记录用户行为
        ec.service.sync().name("marketplace.MarketplaceServices.record#UserBehavior")
            .parameters([
                partyId: publisherId,
                behaviorType: "PUBLISH",
                targetType: "LISTING",
                targetId: listingId,
                behaviorData: [
                    listingType: listingType,
                    category: category,
                    quantity: quantity
                ]
            ]).call()

    } catch (Exception e) {
        logger.error("Error finding matches for listing ${listingId}", e)
    }

    return [
        listingId: listingId,
        status: "success",
        message: "供需信息发布成功"
    ]
}

// 搜索供需信息
Map searchListings() {
    ExecutionContext ec = context.ec

    def findBuilder = ec.entity.find("marketplace.listing.Listing")

    // 添加搜索条件
    if (listingType) findBuilder.condition("listingType", listingType)
    if (category) findBuilder.condition("category", category)
    if (publisherId) findBuilder.condition("publisherId", publisherId)
    if (status) findBuilder.condition("status", status)
    else findBuilder.condition("status", "ACTIVE") // 默认只显示活跃的

    // 分页
    Integer pageIndex = this.pageIndex ?: 0
    Integer pageSize = this.pageSize ?: 20
    findBuilder.offset(pageIndex * pageSize).limit(pageSize)

    // 排序
    String orderBy = this.orderBy ?: "-lastUpdatedStamp"
    findBuilder.orderBy(orderBy)

    EntityList listings = findBuilder.list()
    Long totalCount = findBuilder.count()

    return [
        listings: listings,
        totalCount: totalCount,
        pageIndex: pageIndex,
        pageSize: pageSize
    ]
}

// 查找匹配
Map findMatches() {
    ExecutionContext ec = context.ec
    Logger logger = LoggerFactory.getLogger("MarketplaceServices")

    if (!listingId) {
        ec.message.addError("缺少必填参数：listingId")
        return [:]
    }

    try {
        SmartMatchingEngine engine = new SmartMatchingEngine(ec)
        Integer maxResults = this.maxResults ?: 10
        BigDecimal minScore = this.minScore ?: new BigDecimal("0.6")

        List<Map<String, Object>> matches = engine.findMatchesForListing(
            listingId, maxResults, minScore)

        logger.info("Found ${matches.size()} matches for listing ${listingId}")

        return [
            matches: matches,
            listingId: listingId,
            totalFound: matches.size()
        ]

    } catch (Exception e) {
        logger.error("Error finding matches for listing ${listingId}", e)
        ec.message.addError("查找匹配时发生错误：${e.message}")
        return [:]
    }
}

// 确认匹配（授权联系）
Map confirmMatch() {
    ExecutionContext ec = context.ec
    Logger logger = LoggerFactory.getLogger("MarketplaceServices")

    if (!matchId || !confirmingPartyId) {
        ec.message.addError("缺少必填参数：matchId, confirmingPartyId")
        return [:]
    }

    // 获取匹配记录
    EntityValue match = ec.entity.find("marketplace.match.Match")
        .condition("matchId", matchId)
        .one()

    if (!match) {
        ec.message.addError("匹配记录不存在：${matchId}")
        return [:]
    }

    // 更新匹配状态
    match.status = "CONTACTED"
    match.contactedDate = ec.user.nowTimestamp
    match.store()

    // 获取双方联系信息
    EntityValue supplier = ec.entity.find("mantle.party.Party")
        .condition("partyId", match.getString("supplyListingId"))
        .one()
    EntityValue demander = ec.entity.find("mantle.party.Party")
        .condition("partyId", match.getString("demandListingId"))
        .one()

    // 记录行为
    ec.service.sync().name("marketplace.MarketplaceServices.record#UserBehavior")
        .parameters([
            partyId: confirmingPartyId,
            behaviorType: "CONTACT",
            targetType: "MATCH",
            targetId: matchId
        ]).call()

    logger.info("Match ${matchId} confirmed by party ${confirmingPartyId}")

    return [
        matchId: matchId,
        status: "success",
        message: "联系授权成功",
        contactInfo: [
            // 根据确认方返回对方的联系信息
            // TODO: 实现联系信息获取逻辑
        ]
    ]
}

// 记录用户行为
Map recordUserBehavior() {
    ExecutionContext ec = context.ec

    if (!partyId || !behaviorType) {
        ec.message.addError("缺少必填参数：partyId, behaviorType")
        return [:]
    }

    // 创建行为记录
    EntityValue behavior = ec.entity.makeValue("marketplace.user.UserBehavior")
    behavior.setAll([
        behaviorId: "BEHAVIOR_" + System.currentTimeMillis(),
        partyId: partyId,
        behaviorType: behaviorType,
        targetType: targetType,
        targetId: targetId,
        behaviorData: behaviorData as String, // JSON字符串
        sessionId: sessionId,
        behaviorDate: ec.user.nowTimestamp
    ])
    behavior.create()

    // 异步更新用户画像
    ec.service.async().name("marketplace.MarketplaceServices.rebuild#UserProfile")
        .parameters([partyId: partyId])
        .call()

    return [
        behaviorId: behavior.behaviorId,
        status: "success"
    ]
}

// 获取用户画像
Map getUserProfile() {
    ExecutionContext ec = context.ec

    if (!partyId) {
        ec.message.addError("缺少必填参数：partyId")
        return [:]
    }

    EntityValue profile = ec.entity.find("marketplace.user.UserProfile")
        .condition("partyId", partyId)
        .one()

    if (!profile) {
        // 如果没有画像，创建默认画像
        profile = ec.entity.makeValue("marketplace.user.UserProfile")
        profile.setAll([
            partyId: partyId,
            userType: "CUSTOMER", // 默认为客户
            creditScore: new BigDecimal("0.5"),
            responseRate: new BigDecimal("1.0"),
            matchSuccessRate: new BigDecimal("0.0"),
            totalListings: 0L,
            totalMatches: 0L,
            totalOrders: 0L,
            createdDate: ec.user.nowTimestamp,
            lastUpdatedStamp: ec.user.nowTimestamp
        ])
        profile.create()
    }

    return [
        profile: profile,
        partyId: partyId
    ]
}

// 重建用户画像
Map rebuildUserProfile() {
    ExecutionContext ec = context.ec
    Logger logger = LoggerFactory.getLogger("MarketplaceServices")

    if (!partyId) {
        ec.message.addError("缺少必填参数：partyId")
        return [:]
    }

    logger.info("Rebuilding user profile for party: ${partyId}")

    // 获取或创建用户画像
    EntityValue profile = ec.entity.find("marketplace.user.UserProfile")
        .condition("partyId", partyId)
        .one()

    if (!profile) {
        profile = ec.entity.makeValue("marketplace.user.UserProfile")
        profile.partyId = partyId
        profile.createdDate = ec.user.nowTimestamp
    }

    // 统计用户数据
    Long totalListings = ec.entity.find("marketplace.listing.Listing")
        .condition("publisherId", partyId)
        .count()

    Long totalMatches = ec.entity.find("marketplace.match.Match")
        .condition("supplyListingId", partyId)
        .count() +
        ec.entity.find("marketplace.match.Match")
        .condition("demandListingId", partyId)
        .count()

    Long totalOrders = ec.entity.find("marketplace.order.MatchOrder")
        .condition("sellerId", partyId)
        .count() +
        ec.entity.find("marketplace.order.MatchOrder")
        .condition("buyerId", partyId)
        .count()

    // 计算信用评分（基于历史表现）
    BigDecimal creditScore = new BigDecimal("0.5") // 基础分
    if (totalOrders > 0) {
        // 基于订单完成率和评分计算
        // TODO: 实现更复杂的信用评分算法
        creditScore = new BigDecimal("0.8")
    }

    // 更新画像
    profile.setAll([
        totalListings: totalListings,
        totalMatches: totalMatches,
        totalOrders: totalOrders,
        creditScore: creditScore,
        lastUpdatedStamp: ec.user.nowTimestamp
    ])

    if (profile.isCreate()) {
        profile.create()
    } else {
        profile.update()
    }

    logger.info("User profile rebuilt for party ${partyId}: ${totalListings} listings, ${totalMatches} matches, ${totalOrders} orders")

    return [
        partyId: partyId,
        profile: profile,
        status: "success"
    ]
}

// 获取marketplace统计信息
Map getMarketplaceStats() {
    ExecutionContext ec = context.ec

    // 活跃供应数
    Long activeSupplies = ec.entity.find("marketplace.listing.Listing")
        .condition("listingType", "SUPPLY")
        .condition("status", "ACTIVE")
        .count()

    // 活跃需求数
    Long activeDemands = ec.entity.find("marketplace.listing.Listing")
        .condition("listingType", "DEMAND")
        .condition("status", "ACTIVE")
        .count()

    // 总匹配数
    Long totalMatches = ec.entity.find("marketplace.match.Match").count()

    // 今日新增
    Timestamp todayStart = new Timestamp(System.currentTimeMillis() -
        (System.currentTimeMillis() % (24 * 60 * 60 * 1000)))

    Long todayListings = ec.entity.find("marketplace.listing.Listing")
        .condition("createdDate", EntityCondition.GREATER_THAN_EQUAL_TO, todayStart)
        .count()

    return [
        activeSupplies: activeSupplies,
        activeDemands: activeDemands,
        totalMatches: totalMatches,
        todayListings: todayListings,
        timestamp: ec.user.nowTimestamp
    ]
}