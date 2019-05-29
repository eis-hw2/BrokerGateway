package cn.pipipan.eisproject.brokergatewayddd.axonframework.listener;

import cn.pipipan.eisproject.brokergatewayddd.axonframework.event.MarketDepthFixedEvent;
import cn.pipipan.eisproject.brokergatewayddd.repository.MarketDepthDTORepository;
import com.alibaba.fastjson.JSON;
import com.google.gson.JsonObject;
import org.axonframework.eventhandling.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MarketDepthListener {
    Logger logger = LoggerFactory.getLogger(MarketDepthListener.class);
    @Autowired
    MarketDepthDTORepository marketDepthDTORepository;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @EventHandler
    public void on(MarketDepthFixedEvent marketDepthFixedEvent){
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("marketQuotation", JSON.toJSONString(marketDepthFixedEvent.getMarketQuotation()));
        jsonObject.addProperty("marketDepth", JSON.toJSONString(marketDepthFixedEvent.getMarketDepthDTO()));
        logger.info("jsonObject: {}", jsonObject.toString());
        rabbitTemplate.convertAndSend("marketDepth", "marketDepth", jsonObject.toString());
        marketDepthDTORepository.save(marketDepthFixedEvent.getMarketDepthDTO());
    }
}
