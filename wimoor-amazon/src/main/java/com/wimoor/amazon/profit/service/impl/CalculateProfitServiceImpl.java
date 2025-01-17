package com.wimoor.amazon.profit.service.impl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wimoor.amazon.api.ErpClientOneFeign;
import com.wimoor.amazon.auth.pojo.entity.AmazonAuthority;
import com.wimoor.amazon.auth.pojo.entity.Marketplace;
import com.wimoor.amazon.auth.service.IMarketplaceService;
import com.wimoor.amazon.common.mapper.DimensionsInfoMapper;
import com.wimoor.amazon.common.mapper.FBAShipCycleMapper;
import com.wimoor.amazon.common.pojo.entity.DimensionsInfo;
import com.wimoor.amazon.common.pojo.entity.FBAShipCycle;
import com.wimoor.amazon.product.mapper.ProductCategoryMapper;
import com.wimoor.amazon.product.mapper.ProductInOptMapper;
import com.wimoor.amazon.product.pojo.entity.ProductCategory;
import com.wimoor.amazon.product.pojo.entity.ProductInOpt;
import com.wimoor.amazon.product.pojo.entity.ProductInfo;
import com.wimoor.amazon.profit.pojo.entity.ProfitConfig;
import com.wimoor.amazon.profit.pojo.entity.ReferralFee;
import com.wimoor.amazon.profit.pojo.vo.CostDetail;
import com.wimoor.amazon.profit.pojo.vo.InputDimensions;
import com.wimoor.amazon.profit.service.ICalculateProfitService;
import com.wimoor.amazon.profit.service.IProfitCfgService;
import com.wimoor.amazon.profit.service.IProfitService;
import com.wimoor.amazon.profit.service.IReferralFeeService;
import com.wimoor.amazon.report.mapper.FBAEstimatedFeeMapper;
import com.wimoor.amazon.report.pojo.entity.FBAEstimatedFee;
import com.wimoor.common.result.Result;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
 
@Service("calculateProfitService")  
@RequiredArgsConstructor
public class CalculateProfitServiceImpl implements ICalculateProfitService {
	@Resource
	IProfitCfgService profitCfgService;
	@Autowired
	IMarketplaceService marketplaceService;
	@Autowired
	ProductInOptMapper productInOptMapper;
	final FBAEstimatedFeeMapper fbaEstimatedFeeMapper;
	final ProductCategoryMapper productCategoryMapper;
	final IReferralFeeService referralFeeService;
	final IProfitService profitService;
	final FBAShipCycleMapper fBAShipCycleMapper;
	final DimensionsInfoMapper dimensionsInfoMapper;
    @Autowired
    ErpClientOneFeign erpClientOneFeign;
	public CostDetail getProfit(ProductInfo info, BigDecimal price, AmazonAuthority auth,DimensionsInfo dim_local,BigDecimal cost)   {
		if (price == null || price.floatValue() == 0.0) {
			return null;
		}
		//使用opt上面带的利润id(假如设置了)
		ProductInOpt proopt = productInOptMapper.selectById(info.getId());
		// 获取用户输入信息
		InputDimensions inputDimension_local = null;
		InputDimensions inputDimension_amz = null;
       
		DimensionsInfo dim_amz =null;
		
		if(info!=null&&info.getPageDimensions()!=null) {
			dim_amz= dimensionsInfoMapper.selectById(info.getPageDimensions());
		}
		if (dim_amz == null) {
			dim_amz = dim_local;
		}
		if (dim_local != null) {
			inputDimension_local = dim_local.getInputDimensions();
			inputDimension_amz = dim_amz.getInputDimensions();
		} else {
			return null;
		}
	
		Marketplace market = marketplaceService.selectByPKey(info.getMarketplaceid());
		String country = market.getMarket();
		String profitcfgid=null;
	
		if(proopt!=null) {
			if(proopt.getProfitid()!=null) {
				profitcfgid=proopt.getProfitid().toString();
			}
		} 
		if(StrUtil.isEmpty(profitcfgid)) {
			profitcfgid = profitCfgService.findDefaultPlanIdByGroup(auth.getGroupid());
		}
		ProfitConfig profitcfg = profitCfgService.findConfigAction(profitcfgid);
		ReferralFee ref = new ReferralFee();
		LambdaQueryWrapper<ProductCategory> queryWrapper=new LambdaQueryWrapper<ProductCategory>();
	 
		queryWrapper.eq(ProductCategory::getPid, info.getId());
		queryWrapper.isNotNull(ProductCategory::getParentid);
		List<ProductCategory> categorylist = productCategoryMapper.selectList(queryWrapper);
		ProductCategory category=null;
		if(categorylist!=null&&categorylist.size()>0) {
			  category= categorylist.get(0);
		}
		if (category!=null && category.getName()!=null) {
			ref = referralFeeService.findByPgroup(category.getName().trim(), country);
		} else {
            if(ref==null||ref.getId()==null) {
            	ref = referralFeeService.findCommonOther(country);
            }
		}
		if(ref==null||ref.getId()==null) {
			return null;
		}
		int typeid = ref.getId();
		String type = ref.getType();
		String isMedia = this.profitService.isMedia(typeid);// 是否为媒介
		BigDecimal shipmentfee = null;
		FBAShipCycle stockCycle = null;
		if ("EU".equals(market.getRegion())) {
			stockCycle = fBAShipCycleMapper.findShipCycleBySKU(info.getSku(), "EU", auth.getGroupid());
		} else {
			stockCycle = fBAShipCycleMapper.findShipCycleBySKU(info.getSku(), market.getMarketplaceid(), auth.getGroupid());
		}
		if (stockCycle != null) {
			shipmentfee = stockCycle.getFirstLegCharges();// 头程运费
		}
		CostDetail deatail = null;
		try {
			FBAEstimatedFee fbaFee = null;
			LambdaQueryWrapper<FBAEstimatedFee> queryFbaFee=new LambdaQueryWrapper<FBAEstimatedFee>();
			queryFbaFee.eq(FBAEstimatedFee::getSku, info.getSku());
			queryFbaFee.eq(FBAEstimatedFee::getAsin, info.getAsin());
			queryFbaFee.eq(FBAEstimatedFee::getAmazonauthid, auth.getId());
			queryFbaFee.eq(FBAEstimatedFee::getMarketplaceid, market.getMarketplaceid());
			fbaFee = fbaEstimatedFeeMapper.selectOne(queryFbaFee);
			boolean inSnl = info.getInSnl() == null ? false : info.getInSnl();
			if (fbaFee == null) {
				deatail = this.profitService.getProfitByLocalData(country, profitcfg, inputDimension_amz,
						inputDimension_local, isMedia, type, typeid, cost, "RMB", price, "local", inSnl, shipmentfee);
			}else {
				if (fbaFee != null && deatail == null) {
					ref = referralFeeService.findByPgroup(fbaFee.getProductGroup(), country);
					deatail = this.profitService.getProfitByAmazonData(country, profitcfg, inputDimension_local, isMedia,
							cost, "RMB", price, fbaFee, ref, inSnl, shipmentfee);
				}
			}
	
		} catch (Exception e) {
			e.printStackTrace();
		}  
		return deatail;
	}
	
	 
}
