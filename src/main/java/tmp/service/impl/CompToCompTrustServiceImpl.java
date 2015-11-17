package tmp.service.impl;

import org.springframework.stereotype.Service;
import tmp.bo.HistoryAndWeight;
import tmp.dao.ComponentHistoryMapper;
import tmp.dao.ComponentMapper;
import tmp.dao.ComponentTrustValueMapper;
import tmp.dao.ProviderTrustValueMapper;
import tmp.entity.Component;
import tmp.entity.ComponentHistory;
import tmp.entity.ComponentTrustValue;
import tmp.entity.ProviderTrustValue;
import tmp.service.CompToCompTrustService;
import tmp.staticValue.staticValue;
import tmp.util.ListUtil;
import tmp.util.Weight;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by shining.cui on 2015/11/6.
 */
@Service("compToCompTrustService")
public class CompToCompTrustServiceImpl implements CompToCompTrustService {
    @Resource
    private ComponentHistoryMapper componentHistoryMapper;
    @Resource
    private ProviderTrustValueMapper providerTrustValueMapper;
    @Resource
    private ComponentTrustValueMapper componentTrustValueMapper;
    @Resource
    private ComponentMapper componentMapper;
    @Override
    public BigDecimal calcCompToCompTrust(Component trustor, Component trustee) {
        String trustorUid = trustor.getUid();
        String trusteeUid = trustee.getUid();
        BigDecimal overallTrust;
        //查询组件与组件的交互历史次数
        List<ComponentHistory> componentHistories = componentHistoryMapper.selectByTrustorAndTrusteeUid(trustorUid, trusteeUid, null);
        int directTimes = componentHistories.size();
        //查询组件与所有组件的交互历史次数
        List<ComponentHistory> histories = componentHistoryMapper.selectByTrustorAndTrusteeUid(trustorUid, null, 1);
        int totalTimes = histories.size();
        //计算直接信任与间接信任
        BigDecimal directTrust = calcCompToCompDirectTrust(trustor,trustee);
        BigDecimal indirectTrust = calcCompToCompIndirectTrust(trustor,trustee);
        //根据交互次数分配直接信任与间接信任的权重
        if (directTimes >= staticValue.activeTimesThreshold) {
            overallTrust = directTrust;
        } else if (directTimes == 0) {
            overallTrust = indirectTrust;
        } else {
            BigDecimal weight = Weight.calcDirectTrustWeight(directTimes, totalTimes);
            overallTrust = weight.multiply(directTrust).add(BigDecimal.ONE.subtract(weight).multiply(indirectTrust));
        }
        //将全局信任评估结果保存到数据库
        ComponentTrustValue componentTrustValue = new ComponentTrustValue();
        componentTrustValue.setCreatetime(new Date());
        componentTrustValue.setTrustValue(overallTrust);
        componentTrustValue.setTrusteeUid(trusteeUid);
        componentTrustValue.setTrustorUid(trustorUid);
        //uid为唯一键，作为唯一流水号
        componentTrustValue.setUid("" + new Date().getTime() + trustorUid);
        componentTrustValueMapper.insertSelective(componentTrustValue);
        return overallTrust;
    }

    @Override
    public BigDecimal calcCompToCompDirectTrust(Component trustor, Component trustee) {
        String trustorUid = trustor.getUid();
        String trusteeUid = trustee.getUid();
        BigDecimal directTrust;
        //获取双方实体所有交互历史
        List<ComponentHistory> componentHistories = componentHistoryMapper.selectByTrustorAndTrusteeUid(trustorUid, trusteeUid, null);
        if (componentHistories.size() == 0) {
            return BigDecimal.ZERO;
        }
        //获取双方实体可用交互历史
        List<HistoryAndWeight<ComponentHistory>> histories = ListUtil.getAvailableComponentHistory(componentHistories, staticValue.daysThreshold);
        if (histories.size() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = BigDecimal.ZERO;
        //计算每次交互历史的信任值与权重，求累成和
        for (HistoryAndWeight<ComponentHistory> historyAndWeight : histories) {
            BigDecimal trustValue = historyAndWeight.getHistory().getTrustValue();
            BigDecimal weight = historyAndWeight.getWeight();
            sum = sum.add(trustValue.multiply(weight));
        }
        //获得组件所属云的最近一次信誉值,如果该云没有信誉值，则默认为0.5
        BigDecimal providerTrust;
        if (trustor.getParentUid().equals(trustee.getParentUid())) {
            providerTrust = BigDecimal.ONE;
        } else {
            ProviderTrustValue providerTrustValue = providerTrustValueMapper.queryLatestByProviderUid(trustee.getParentUid());
            if (providerTrustValue == null) {
                providerTrust = new BigDecimal(0.5);
            } else {
                providerTrust = providerTrustValue.getTrustValue();
            }
        }
        //计算有效交互历史的次数，累成和除以次数即为直接信任
        BigDecimal times = new BigDecimal(histories.size());
        directTrust = sum.divide(times,4,BigDecimal.ROUND_HALF_UP).multiply(providerTrust);
        return directTrust;
    }

    @Override
    public BigDecimal calcCompToCompIndirectTrust(Component trustor, Component trustee) {
        String trustorUid = trustor.getUid();
        String trusteeUid = trustee.getUid();
        BigDecimal indirectTrust;
        //查询与该组件有直接信任的组件，作为推荐组件
        List<ComponentTrustValue> componentTrustValues = componentTrustValueMapper.selectByTrustorAndTrusteeUid(null, trusteeUid);
        //获得推荐人名单，去重，去自身
        List<String> recommenders = new ArrayList<String>();
        for (ComponentTrustValue trustValues : componentTrustValues) {
            String uid = trustValues.getTrustorUid();
            if (uid.equals(trustorUid)) {
                continue;
            }
            if (!recommenders.contains(uid)) {
                recommenders.add(uid);
            }
        }
        //若果没有推荐人，则无推荐信任
        if (recommenders.size() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = BigDecimal.ZERO;
        //有效推荐者次数
        int times = 0;
        //计算推荐信任
        for (String recommenderUid : recommenders) {
            //查询推荐者对组件的最近信任值
            ComponentTrustValue recommenderToRenterTrust = componentTrustValueMapper.queryLatestTrustValue(recommenderUid, trusteeUid);
            BigDecimal recommenderDirectTrustValue = recommenderToRenterTrust.getTrustValue();
            //查询请求者对推荐者的最近信任值
            ComponentTrustValue componentToRecommenderTrust = componentTrustValueMapper.queryLatestTrustValue(trustorUid, recommenderUid);
            BigDecimal componentToRecommenderTrustValue;
            if (componentToRecommenderTrust == null) {
                continue;
            } else {
                componentToRecommenderTrustValue = componentToRecommenderTrust.getTrustValue();
                times++;
            }
            //查询推荐者所属云的信誉
            BigDecimal recommendersProviderTrust;
            Component recommender = componentMapper.selectByUid(recommenderUid);
            //如果推荐者与请求者属于同一个云，则默认完全信任
            if (recommender.getParentUid().equals(trustor.getParentUid())) {
                recommendersProviderTrust = BigDecimal.ONE;
            } else {
                ProviderTrustValue providerTrustValue = providerTrustValueMapper.queryLatestByProviderUid(recommender.getParentUid());
                //获得组件所属云的最近一次信誉值,如果该云没有信誉值，则默认为0.5
                if (providerTrustValue == null) {
                    recommendersProviderTrust = new BigDecimal(0.5);
                } else {
                    recommendersProviderTrust = providerTrustValue.getTrustValue();
                }
            }
            sum = sum.add(recommenderDirectTrustValue
                    .multiply(componentToRecommenderTrustValue)
                    .multiply(recommendersProviderTrust));
        }
        if (times == 0) {
            return BigDecimal.ZERO;
        }
        indirectTrust = sum.divide(new BigDecimal(times),4,BigDecimal.ROUND_HALF_UP);
        return indirectTrust;
    }
}
