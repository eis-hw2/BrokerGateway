package cn.pipipan.eisproject.brokergatewayddd.domain;

import cn.pipipan.eisproject.brokergatewayddd.util.DTOConvert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;

public class LimitOrder {
    Logger logger = LoggerFactory.getLogger(LimitOrder.class);
    static class Convert implements DTOConvert<LimitOrder, LimitOrderDTO>{
        @Override
        public LimitOrderDTO convertFrom(LimitOrder limitOrder) {
            LimitOrderDTO limitOrderDTO = new LimitOrderDTO();
            BeanUtils.copyProperties(limitOrder, limitOrderDTO);
            return limitOrderDTO;
        }
    }

    public LimitOrderDTO convertToLimitOrderDTO(){
        Convert convert = new Convert();
        return convert.convertFrom(this);
    }

    private String id;
    private String marketDepthId;
    private int totalCount;
    private int count;
    private int unitPrice;
    private Side side;
    private Status status;
    private String creationTime;
    private String traderName;
    private String futureName;
    private String clientId;

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public int getTotalCount() {
        return totalCount;
    }
    public String getFutureName() {
        return futureName;
    }

    public void setFutureName(String futureName) {
        this.futureName = futureName;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public String getTraderName() {
        return traderName;
    }

    public void setTraderName(String traderName) {
        this.traderName = traderName;
    }


    public String getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(String creationTime) {
        this.creationTime = creationTime;
    }

    public void decreaseCount(int delta){
        this.count -= delta;
        if (this.count == 0) this.status = Status.FINISHED;
    }

    boolean isBuyer(){
        return this.side.equals(Side.BUYER);
    }

    public String getId() {
        return id;
    }

    public String getMarketDepthId() {
        return marketDepthId;
    }

    public int getCount() {
        return count;
    }

    public int getUnitPrice() {
        return unitPrice;
    }

    public Side getSide() {
        return side;
    }

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setMarketDepthId(String marketDepthId) {
        this.marketDepthId = marketDepthId;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public void setUnitPrice(int unitPrice) {
        this.unitPrice = unitPrice;
    }

    public void setSide(Side side) {
        this.side = side;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Status getStatus() {
        return status;
    }
}
