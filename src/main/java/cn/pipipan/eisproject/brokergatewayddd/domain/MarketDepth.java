package cn.pipipan.eisproject.brokergatewayddd.domain;

import cn.pipipan.eisproject.brokergatewayddd.axonframework.command.*;
import cn.pipipan.eisproject.brokergatewayddd.axonframework.event.*;
import cn.pipipan.eisproject.brokergatewayddd.domain.NullObject.NullBuyerLimitOrder;
import cn.pipipan.eisproject.brokergatewayddd.domain.NullObject.NullSellerLimitOrder;
import cn.pipipan.eisproject.brokergatewayddd.helper.Util;
import cn.pipipan.eisproject.brokergatewayddd.util.DTOConvert;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

@Aggregate
public class MarketDepth {
    Logger logger = LoggerFactory.getLogger(MarketDepth.class);
    @Autowired
    CommandGateway commandGateway;

    class Convert implements DTOConvert<MarketDepth, MarketDepthDTO> {
        @Override
        public MarketDepthDTO convertFrom(MarketDepth marketDepth) {
            MarketDepthDTO marketDepthDTO = new MarketDepthDTO();
            marketDepthDTO.id = marketDepth.id;
            for (int i = marketDepth.buyers.size() - 1; i >= Math.max(0, marketDepth.buyers.size() - 5); i--) {
                OrderPriceComposite orderPriceComposite = buyers.get(i);
                marketDepthDTO.addBuyer(orderPriceComposite.getLimitOrders().stream().mapToInt(LimitOrder::getCount).sum(), orderPriceComposite.getPrice());
            }
            for (int i = 0; i < Math.min(5, marketDepth.sellers.size()); i++) {
                OrderPriceComposite orderPriceComposite = sellers.get(i);
                marketDepthDTO.addSeller(orderPriceComposite.getLimitOrders().stream().mapToInt(LimitOrder::getCount).sum(), orderPriceComposite.getPrice());
            }
            return marketDepthDTO;
        }
    }

    public MarketDepthDTO convertToMarketDepthDTO() {
        Convert convert = new Convert();
        return convert.convertFrom(this);
    }

    private class OrderPriceComposite {
        int price;
        List<LimitOrder> limitOrders;

        OrderPriceComposite() {
        }

        OrderPriceComposite(LimitOrder limitOrder) {
            this.price = limitOrder.getUnitPrice();
            this.limitOrders = new ArrayList<>();
            this.limitOrders.add(limitOrder);
        }

        int getPrice() {
            return price;
        }

        void setPrice(int price) {
            this.price = price;
        }

        List<LimitOrder> getLimitOrders() {
            return limitOrders;
        }

        void setLimitOrders(List<LimitOrder> limitOrders) {
            this.limitOrders = limitOrders;
        }

        void addOrder(LimitOrder order) {
            this.limitOrders.add(order);
        }
    }

    

    @AggregateIdentifier
    private String id;

    private List<OrderPriceComposite> sellers;
    private List<OrderPriceComposite> buyers;
    private List<MarketOrder> marketOrders;
    private List<StopOrder> stopOrders;
    private MarketQuotation marketQuotation;

    private LimitOrder getFirstBuyer(){
        if (buyers.size() == 0) return new NullBuyerLimitOrder();
        return buyers.get(buyers.size() - 1).getLimitOrders().get(0);
    }

    private LimitOrder getFirstSeller(){
        if (sellers.size() == 0) return new NullSellerLimitOrder();
        return sellers.get(0).getLimitOrders().get(0);
    }

    private MarketOrder getFirstMarketOrder() {
        for (MarketOrder marketOrder : marketOrders){
            if ((marketOrder.isBuyer() && !sellers.isEmpty())
                    || (marketOrder.isSeller() && !buyers.isEmpty())) {
                return marketOrder;
            }
        }
        return null;
    }

    private boolean canMarketOrderDone() {
        return getFirstMarketOrder() != null;
    }

    public MarketDepth(String id) {
        AggregateLifecycle.apply(new IssueMarketDepthEvent(id));
    }

    @CommandHandler
    public void handle(IssueLimitOrderCommand issueLimitOrderCommand){
        AggregateLifecycle.apply(new IssueLimitOrderEvent(id, issueLimitOrderCommand.getLimitOrderDTO()));
    }

    @CommandHandler
    public void handle(IssueMarketOrderCommand issueMarketOrderCommand){
        AggregateLifecycle.apply(new IssueMarketOrderEvent(id, issueMarketOrderCommand.getMarketOrderDTO()));
    }

    @CommandHandler
    public void handle(IssueCancelOrderCommand issueCancelOrderCommand){
        AggregateLifecycle.apply(new IssueCancelOrderEvent(id, issueCancelOrderCommand.getCancelOrder()));
    }

    @CommandHandler
    public void handle(IssueStopOrderCommand issueStopOrderCommand){
        AggregateLifecycle.apply(new IssueStopOrderEvent(id, issueStopOrderCommand.getStopOrder()));
    }

    @CommandHandler
    public void handle(OpenMarketCommand openMarketCommand){
        AggregateLifecycle.apply(new MarketOpenedEvent(id));
    }

    @CommandHandler
    public void handle(CloseMarketCommand closeMarketCommand){
        AggregateLifecycle.apply(new MarketClosedEvent(id, Util.getNowDate()));
    }

    @EventSourcingHandler
    public void on(IssueMarketDepthEvent issueMarketDepthEvent) {
        this.id = issueMarketDepthEvent.getId();
        this.buyers = new ArrayList<>();
        this.sellers = new ArrayList<>();
        this.marketOrders = new ArrayList<>();
        this.stopOrders = new ArrayList<>();
        this.marketQuotation = new MarketQuotation(Util.getNowDate(), 0, id);
    }

    @EventSourcingHandler
    public void on(IssueLimitOrderEvent issueLimitOrderEvent){
        //logger.info("onIssueLimitOrderEvent");
        // insert into waiting queue;
        LimitOrder limitOrder = issueLimitOrderEvent.getLimitOrderDTO().convertToLimitOrder();
        insertIntoWaitingQueue(limitOrder, limitOrder.isBuyer() ? buyers : sellers);
        AggregateLifecycle.apply(new MarketDepthChangedEvent(id));
    }

    @EventSourcingHandler
    public void on(IssueStopOrderEvent issueStopOrderEvent){
        stopOrders.add(issueStopOrderEvent.getStopOrder());
        dealWithStopOrders();
        AggregateLifecycle.apply(new MarketDepthChangedEvent(id));
    }

    @EventSourcingHandler
    public void on(IssueCancelOrderEvent event){
        CancelOrder cancelOrder = event.getCancelOrder();
        Status status = Status.FAILURE;
        switch (cancelOrder.getTargetType()){
            case MarketOrder:
                for (MarketOrder marketOrder : marketOrders){
                    if (marketOrder.getId().equals(cancelOrder.getTargetId())){
                        marketOrders.remove(marketOrder);
                        status = Status.FINISHED;
                        AggregateLifecycle.apply(new MarketOrderCancelledEvent(id, marketOrder.getId()));
                        break;
                    }
                }
                break;
            case LimitOrder:
                List<OrderPriceComposite> waitingComposites = cancelOrder.isBuyer() ? buyers : sellers;
                int index = binaryFindIndex(cancelOrder.getUnitPrice(), waitingComposites);
                if (index == waitingComposites.size() || waitingComposites.get(index).getPrice() != cancelOrder.getUnitPrice())
                    break;
                else {
                    List<LimitOrder> limitOrders = waitingComposites.get(index).getLimitOrders();
                    for (LimitOrder limitOrder : limitOrders){
                        if (limitOrder.getId().equals(cancelOrder.getTargetId())) {
                            limitOrders.remove(limitOrder);
                            if (limitOrders.isEmpty()) waitingComposites.remove(index);
                            status = Status.FINISHED;
                            AggregateLifecycle.apply(new LimitOrderCancelledEvent(id, limitOrder.getId()));
                            break;
                        }
                    }
                }
                break;
            case StopOrder:
                for (StopOrder stopOrder : stopOrders){
                    if (stopOrder.getId().equals(cancelOrder.getTargetId())){
                        stopOrders.remove(stopOrder);
                        status = Status.FINISHED;
                        AggregateLifecycle.apply(new StopOrderCancelledEvent(id, stopOrder.getId()));
                        break;
                    }
                }
                break;
        }
        AggregateLifecycle.apply(new CancelOrderFinishedEvent(id, cancelOrder.getId(), status));
    }

    @EventSourcingHandler
    public void on(IssueMarketOrderEvent issueMarketOrderEvent){
        // insert into waiting queue;
        MarketOrder marketOrder = issueMarketOrderEvent.getMarketOrderDTO().convertToMarketOrder();
        insertIntoMarketOrders(marketOrder);
        AggregateLifecycle.apply(new MarketDepthChangedEvent(id));
    }

    private void insertIntoMarketOrders(MarketOrder marketOrder) {
        marketOrders.add(marketOrder);
    }

    private void insertIntoWaitingQueue(LimitOrder order, List<OrderPriceComposite> waitingComposites, int i) {
        if (i == waitingComposites.size() || waitingComposites.get(i).getPrice() != order.getUnitPrice())
            waitingComposites.add(i, new OrderPriceComposite(order));
        else
            waitingComposites.get(i).addOrder(order);
    }

    private void insertIntoWaitingQueue(LimitOrder order, List<OrderPriceComposite> waitingComposites) {
        int index = binaryFindIndex(order, waitingComposites);
        insertIntoWaitingQueue(order, waitingComposites, index);
    }

    private int binaryFindIndex(LimitOrder order, List<OrderPriceComposite> waitingComposites) {
        return binaryFindIndex(order.getUnitPrice(), waitingComposites);
    }

    private int binaryFindIndex(int price, List<OrderPriceComposite> waitingComposites) {
        //TODO 使用二分查找
        for (int i = 0; i < waitingComposites.size(); ++i) {
            if (waitingComposites.get(i).getPrice() >= price) return i;
        }
        return waitingComposites.size();
    }

    @EventSourcingHandler
    public void on(MarketDepthChangedEvent marketDepthChangedEvent) {
        dealWithStopOrders();
        if (isFixed()) AggregateLifecycle.apply(new MarketDepthFixedEvent(id, convertToMarketDepthDTO(), marketQuotation.clone()));
        else if (canMarketOrderDone()){
            MarketOrder marketOrder = getFirstMarketOrder();
            LimitOrder limitOrder = marketOrder.isBuyer() ? getFirstSeller() : getFirstBuyer();
            dealWithMarketOrders(marketOrder, limitOrder);
        }
        else {
            LimitOrder buyer_order = getFirstBuyer();
            LimitOrder seller_order = getFirstSeller();
            dealWithLimitOrders(buyer_order, seller_order);
        }
    }

    // cur >= buy_limit or cur <= sell_limit
    private void dealWithStopOrders() {
        int currentPrice = (int)marketQuotation.getCurrentPrice();
        int buyerCurrentComparePrice = currentPrice == 0 ? Integer.MIN_VALUE :
                currentPrice;
        int sellerCurrentComparePrice = currentPrice == 0 ? Integer.MAX_VALUE :
                currentPrice;
        Iterator<StopOrder> iterator = stopOrders.iterator();
        while(iterator.hasNext()){
            StopOrder stopOrder = iterator.next();
            if ((stopOrder.isBuyer() && stopOrder.getStopPrice() <= buyerCurrentComparePrice)
                || (stopOrder.isSeller() && stopOrder.getStopPrice() >= sellerCurrentComparePrice)) {
                //logger.info("get the converted order");
                switch (stopOrder.getTargetType()){
                    case LimitOrder:
                        LimitOrder limitOrder = stopOrder.convertToLimitOrder();
                        insertIntoWaitingQueue(limitOrder, limitOrder.isBuyer() ? buyers : sellers);
                        AggregateLifecycle.apply(new StopOrderToLimitOrderConvertedEvent(id, stopOrder.getId(), limitOrder.convertToLimitOrderDTO()));
                        break;
                    case MarketOrder:
                        MarketOrder marketOrder = stopOrder.convertToMarketOrder();
                        insertIntoMarketOrders(marketOrder);
                        AggregateLifecycle.apply(new StopOrderToMarketOrderConvertedEvent(id, stopOrder.getId(), marketOrder.convertToMarketOrderDTO()));
                        break;
                }
                iterator.remove();
            }
        }
    }

    private void dealWithMarketOrders(MarketOrder marketOrder, LimitOrder limitOrder) {
        int delta = Math.min(marketOrder.getCount(), limitOrder.getCount());
        decreaseMarketOrderCount(marketOrder, delta);
        decreaseLimitOrderCount(limitOrder, delta);
        OrderBlotterDTO orderBlotterDTO = marketOrder.isBuyer() ? OrderBlotterDTO.createOrderBlotter(delta, limitOrder.getUnitPrice(), marketOrder.convertToMarketOrderDTO(), limitOrder.convertToLimitOrderDTO(), this.id) :
                OrderBlotterDTO.createOrderBlotter(delta, limitOrder.getUnitPrice(), limitOrder.convertToLimitOrderDTO(), marketOrder.convertToMarketOrderDTO(), this.id);
        marketQuotation.update(orderBlotterDTO);
        AggregateLifecycle.apply(new IssueOrderBlotterEvent(id, orderBlotterDTO));
        AggregateLifecycle.apply(new MarketDepthChangedEvent(id));
    }


    private void dealWithLimitOrders(LimitOrder buyer_order, LimitOrder seller_order) {
        int delta = Math.min(buyer_order.getCount(), seller_order.getCount());
        decreaseLimitOrderCount(buyer_order, delta);
        decreaseLimitOrderCount(seller_order, delta);
        OrderBlotterDTO orderBlotterDTO = OrderBlotterDTO.createOrderBlotter(delta, calculatePrice(buyer_order, seller_order), buyer_order.convertToLimitOrderDTO(), seller_order.convertToLimitOrderDTO(), this.id);
        marketQuotation.update(orderBlotterDTO);
        AggregateLifecycle.apply(new IssueOrderBlotterEvent(id, orderBlotterDTO));
        AggregateLifecycle.apply(new MarketDepthChangedEvent(id));
    }

    private int calculatePrice(LimitOrder buyer_order, LimitOrder seller_order) {
        Date buyer_order_date = Util.parseString(buyer_order.getCreationTime());
        Date seller_order_date = Util.parseString(seller_order.getCreationTime());
        try {
            return buyer_order_date.before(seller_order_date) ? buyer_order.getUnitPrice() : seller_order.getUnitPrice();
        }
        catch (Exception e){
            return buyer_order.getUnitPrice();
        }
    }


    private void decreaseMarketOrderCount(MarketOrder marketOrder, int delta) {
        marketOrder.decreaseCount(delta);
        if (marketOrder.getCount() == 0) marketOrders.remove(marketOrder);
        AggregateLifecycle.apply(new MarketOrderCountDecreasedEvent(id, marketOrder.convertToMarketOrderDTO()));
    }

    private void decreaseLimitOrderCount(LimitOrder limitOrder, int delta) {
        limitOrder.decreaseCount(delta);
        List<OrderPriceComposite> waitingComposites = limitOrder.isBuyer() ? buyers : sellers;
        int index = limitOrder.isBuyer() ? buyers.size() - 1 : 0;
        if (limitOrder.getCount() == 0) {
            List<LimitOrder> limitOrders = waitingComposites.get(index).getLimitOrders();
            limitOrders.remove(0);
            if (limitOrders.isEmpty()) waitingComposites.remove(index);
        }
        AggregateLifecycle.apply(new LimitOrderCountDecreasedEvent(id, limitOrder.convertToLimitOrderDTO()));
    }

    private boolean isFixed() {
        return !canMarketOrderDone() && (buyers.isEmpty() || sellers.isEmpty() || buyers.get(buyers.size() - 1).getPrice() < sellers.get(0).getPrice());
    }

    protected MarketDepth() {

    }

    @EventSourcingHandler
    public void on(MarketOpenedEvent marketOpenedEvent){
        marketQuotation = new MarketQuotation(marketQuotation);
    }

    @EventSourcingHandler
    public void on(MarketClosedEvent marketClosedEvent){
        marketQuotation.setClosePrice(marketQuotation.getCurrentPrice());
        AggregateLifecycle.apply(new IssueMarketQuotationEvent(id, marketQuotation.clone()));
        clearMarket();
    }

    private void clearMarket() {
        buyers.clear();
        sellers.clear();
        marketOrders.clear();
        stopOrders.clear();

    }
}
