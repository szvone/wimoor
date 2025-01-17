package com.wimoor.amazon.summary.service.impl;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.annotation.Resource;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.wimoor.amazon.api.ErpClientOneFeign;
import com.wimoor.amazon.product.pojo.entity.ProductInOpt;
import com.wimoor.amazon.product.pojo.entity.ProductInfo;
import com.wimoor.amazon.product.service.IProductInOptService;
import com.wimoor.amazon.product.service.IProductInfoService;
import com.wimoor.amazon.report.mapper.OrdersReportMapper;
import com.wimoor.amazon.report.mapper.OrdersSummaryMapper;
import com.wimoor.amazon.report.mapper.SummaryAllMapper;
import com.wimoor.amazon.summary.service.IOrdersSumService;
import com.wimoor.common.GeneralUtil;
import com.wimoor.common.mvc.BizException;
import com.wimoor.common.pojo.entity.Picture;
import com.wimoor.common.result.Result;
import com.wimoor.common.service.IPictureService;

import cn.hutool.core.util.StrUtil;
 
@Service("ordersSumService")  
public class OrdersSumServiceImpl implements IOrdersSumService {
    @Resource
    OrdersSummaryMapper ordersSummaryMapper;
    @Resource
    OrdersReportMapper ordersReportMapper;
    @Resource
    SummaryAllMapper summaryAllMapper;
    @Autowired
    ErpClientOneFeign erpClientOneFeign;
    @Autowired
    IProductInfoService iProductInfoService;
    @Autowired
    IProductInOptService iProductInOptService;
    @Autowired
    IPictureService iPictureService;
    public static String getKeyByTimeType(Map<String, Object> map,Calendar c){
    	String tempkey="";
    	Integer m= (c.get(Calendar.MONTH)+1);
    	String mstr=m<10?"0"+m.toString():m.toString();
    	Integer d=c.get(Calendar.DAY_OF_MONTH);
    	String dstr=d<10?"0"+d.toString():d.toString();
  	  if("Daily".equals(map.get("bytime"))){
		   tempkey =tempkey+  mstr;
		   tempkey =tempkey+ "-"+dstr ; 
		  }
  	  else if("Weekly".equals(map.get("bytime"))){
  		 tempkey= GeneralUtil.getSundayOfThisWeek(c.getTime());
  		 tempkey=tempkey.substring(tempkey.length()-5, tempkey.length());
  	  }
     else if("Monthly".equals(map.get("bytime"))){
			   tempkey =tempkey+  (c.get(Calendar.YEAR));
			   tempkey =tempkey+ "-"+ m; 
		  }
		return tempkey;
    }

    public static Boolean checkTimeType(Map<String, Object> map,Calendar c,Date beginDate,Date endDate){
    	 Calendar calendar = Calendar.getInstance();      
    	    if(endDate != null){        
    	         calendar.setTime(endDate);      
    	    }        
    	    //int w = calendar.get(Calendar.DAY_OF_WEEK)  ;  
    	    int m = calendar.get(Calendar.MONTH);
    	    int y = calendar.get(Calendar.YEAR);
      if(c.get(Calendar.YEAR)<y) return true;
  	  if("Daily".equals(map.get("bytime"))){
		     return c.getTime().equals(endDate)||c.getTime().before(endDate);
	  }
  	  else if("Weekly".equals(map.get("bytime"))){
  		    return c.getTime().equals(endDate)||  GeneralUtil.getSundayOfThisWeek(c.getTime()).compareTo(GeneralUtil.getSundayOfThisWeek(endDate))<=0;
  	  }
	  else if("Monthly".equals(map.get("bytime"))){
		    return  c.get(Calendar.YEAR)==y?c.get(Calendar.MONTH)<=m:c.get(Calendar.YEAR)<y; 
	  }
	return null;
	 
    }
    public static void step(Map<String, Object> map,Calendar c,Date beginDate,Date endDate){
  	  if("Daily".equals(map.get("bytime"))){
		    c.add(Calendar.DATE, 1);
		  }
	  else if("Weekly".equals(map.get("bytime"))){
		    c.add(Calendar.DATE,7); 
		  }
	   else if("Monthly".equals(map.get("bytime"))){
		   c.add(Calendar.MONTH,1); 
		  }
 
	 
    }
     
	
	public List<Map<String, Object>> weekReport(Map<String,Object> map) {
		return   ordersSummaryMapper.weekAmount(map) ;
	}
	
	 
	public int returnCalendarDay(String field, Calendar c, int daysize) {
		if ("last30".equals(field)) {
			c.add(Calendar.DATE, -30);
			daysize = 30;
		}else  if("last60".equals(field)){
			c.add(Calendar.DATE, -60);
			daysize = 60;
		}
		else if("last90".equals(field)){
			c.add(Calendar.DATE, -90);
			daysize = 90;
		}else {
			c.add(Calendar.DATE, -180);
			daysize = 180;
		}
		return daysize;
	}
	 
	
	
	public Map<String, Object> getOrderSumField(Map<String, Object> parameter) {
		List<Map<String, Object>> ordermap = ordersSummaryMapper.getOrderSumField(parameter);
		TreeMap<String, Object> map = new TreeMap<String, Object>();
		for (Map<String, Object> temp : ordermap) {
			map.put( temp.get("purchase_date").toString().replaceAll("-", ""), temp.get("quantity"));
		}
		Calendar calendar = Calendar.getInstance();
		String endDateStr = (String) parameter.get("endDate");
		String beginDateStr = (String) parameter.get("beginDate");

		Date endDate = null;
		Date beginDate = null;
		if (endDateStr != null && beginDateStr != null) {
			endDate = GeneralUtil.getDatez(endDateStr);
			beginDate = GeneralUtil.getDatez(beginDateStr);
		}
		calendar.setTime(beginDate);
		Date end = endDate;
		for (Date step = calendar.getTime(); step.before(end) || step.equals(end); calendar.add(Calendar.DATE,
				1), step = calendar.getTime()) {
			String field = GeneralUtil.formatDate(step, GeneralUtil.FMT_YMD).replaceAll("-", "");
			if (!map.containsKey(field)) {
				map.put(field, 0);
			}
		}
		return map;
	}
	
	@Cacheable(value = "findordershopReport#1" )
	public List<Map<String, Object>> findordershopReport(Map<String, Object> parameter) {
		// TODO Auto-generated method stub
		List<Map<String, Object>> list = ordersSummaryMapper.findordershopReport(parameter);
		Calendar calendar = Calendar.getInstance();
		String endDateStr = (String) parameter.get("endDate");
		String beginDateStr = (String) parameter.get("beginDate");
		Date endDate = null;
		Date beginDate = null;
		if (endDateStr != null && beginDateStr != null) {
			endDate = GeneralUtil.getDatez(endDateStr);
			beginDate = GeneralUtil.getDatez(beginDateStr);
		}
		calendar.setTime(beginDate);
		Date end = endDate;
		Map<String, Object> fieldmap = new HashMap<String, Object>();
		for (Date step = calendar.getTime(); step.before(end) || step.equals(end); calendar.add(Calendar.DATE,
				1), step = calendar.getTime()) {
			String field = GeneralUtil.formatDate(step, GeneralUtil.FMT_YMD);
			fieldmap.put("v" + field.replace("-", ""), field);
		}
		List<String> materlist = null;
		if(parameter.get("color") != null || parameter.get("owner") != null) {
			 String color="";
			 if(parameter.get("color")!=null) {
				 color=parameter.get("color").toString();
			 }
			 String owner="";
			 if(parameter.get("owner")!=null) {
				 owner=parameter.get("owner").toString();
			 }
			 String shopid="";
			 if(parameter.get("shopid")!=null) {
				 shopid=parameter.get("shopid").toString();
			 }
			 Result<List<String>> result = erpClientOneFeign.findMarterialForColorOwner(shopid,owner,color);
			 if(Result.isSuccess(result)&&result.getData()!=null) {
				 materlist=result.getData();
			 }
		}
		Map<String, Map<String, Object>> map = new HashMap<String, Map<String, Object>>();
		List<Map<String, Object>> sumList = new ArrayList<Map<String, Object>>();
		if(list != null && list.size() > 0) {
			for(Map<String, Object> item:list) {
				String sku = item.get("sku").toString();
				String date = "v" + item.get("purchase_date");
				date = date.replaceAll("-", "");
				int dateSum = item.get("quantity") == null?0:Integer.parseInt(item.get("quantity").toString());
				if(map.get(sku) == null) {
					item.put(date, dateSum);
					item.put("vsum", dateSum);
					map.put(sku, item);
				}else {
					if(map.get(sku).get(date) != null) {
						map.get(sku).put(date, dateSum + Integer.parseInt(map.get(sku).get(date).toString()));
					}else {
						map.get(sku).put(date, dateSum);
					}
					dateSum = (Integer) map.get(sku).get("vsum") + dateSum;
					map.get(sku).put("vsum", dateSum);
				}
			}
			Map<String, Object> sumDate = new HashMap<String, Object>();
			for(String key : map.keySet()) { 
				Map<String, Object> map2 = map.get(key);
				for(String fileKey : fieldmap.keySet()) {
					if(map2.containsKey(fileKey)) {
						continue;
					}
					map2.put(fileKey, 0);
				}
				for(String datekey : map2.keySet()) {
					if(datekey.contains("v")) {
						String newkey = datekey.replaceAll("v", "");
						if(newkey.contains("sum")) newkey = "汇总";	
						if(sumDate.get(newkey) == null) {
							sumDate.put(newkey, map2.get(datekey));
						}else {
							if(newkey.contains("sum")) newkey = "汇总";
							int num = map2.get(datekey) == null?0:Integer.parseInt(map2.get(datekey).toString());
							int num2 = sumDate.get(newkey) == null?0:Integer.parseInt(sumDate.get(newkey).toString());
							sumDate.put(newkey, num+num2);
						}
					}
				}
				String sku = map2.get("sku").toString();
			    String amazonAuthId=map2.get("amazonAuthId").toString();
				String marketplaceid=map2.get("marketplaceid").toString();
				Map<String,Object> info = iProductInfoService.productSimpleInfoOnlyone(amazonAuthId, sku, marketplaceid);
				map2.putAll(info);
				String msku=map2.get("msku").toString();
				if(materlist != null) {
					if(!materlist.contains(msku)) continue;
				}
				String shopid=parameter.get("shopid").toString();
			    Result<Map<String, Object>> material = erpClientOneFeign.findMaterialMapBySku(key, shopid);
				if (map2.get("sku") != null) {
					map2.put("amz_sku", map2.get("sku"));
				}
			    if(material!=null&&Result.isSuccess(material)&&material.getData()!=null){
			    	Object image=map2.get("image");
			    	map2.putAll(material.getData());
			    	if(map2.get("image")==null&&image!=null) {
                            	map2.put("image", image);
			    	}
			    } 
			    
			    sumList.add(map2);
			}
			for(Map<String, Object> item:list) {
				item.put("sumData",sumDate);
			}
		}
		return sumList;
	}
	
	public List<Map<String,String>> getSalesField(Map<String, Object> parameter) {
		List<Map<String,String>> orderlist = new ArrayList<Map<String,String>>();
		Calendar calendar = Calendar.getInstance();
		String endDateStr = (String) parameter.get("endDate");
		String beginDateStr = (String) parameter.get("beginDate");

		Date endDate = null;
		Date beginDate = null;
		if (endDateStr != null && beginDateStr != null) {
			endDate = GeneralUtil.getDatez(endDateStr);
			beginDate = GeneralUtil.getDatez(beginDateStr);
		} else {
			endDate = calendar.getTime();
			calendar.add(Calendar.DATE, -7);
			beginDate = calendar.getTime();
		}
		calendar.setTime(beginDate);
		Date end = endDate;
		SimpleDateFormat FMT_YMD = new SimpleDateFormat("MM月dd日");
		SimpleDateFormat FMT_YMD2 = new SimpleDateFormat("yyyyMMdd");
		for (Date step = calendar.getTime(); 
				step.before(end) || step.equals(end); 
				calendar.add(Calendar.DATE,1), step = calendar.getTime()) {
			String field = GeneralUtil.formatDate(step, FMT_YMD);
			Map<String,String> map=new HashMap<String,String>();
			map.put("name", field); 
			map.put("label", "v"+ GeneralUtil.formatDate(step, FMT_YMD2));
			orderlist.add(map);
		}
		return orderlist;
	}

	public String getStringFromMap(Map<String, Object> parameter, String key) {
		if (parameter.get(key) == null)
			return null;
		return (String) parameter.get(key);
	}
	public SXSSFWorkbook setProductSalasExcelBook(List<Map<String, Object>> list) {
		// TODO Auto-generated method stub
		SXSSFWorkbook workbook = new SXSSFWorkbook();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		if(list != null && list.size() > 0){
			Sheet sheet = workbook.createSheet("sheet1");
			// 在索引0的位置创建行（最顶端的行）
			Row trow = sheet.createRow(0);
			Cell cell = trow.createCell(0); // 在索引0的位置创建单元格(左上端)
			cell.setCellValue("SKU");
			cell = trow.createCell(1); // 在索引1的位置创建单元格(左上端)
			cell.setCellValue("负责人");
			cell = trow.createCell(2); // 在索引1的位置创建单元格(左上端)
			cell.setCellValue("上架日期"); 
			cell = trow.createCell(3); // 在索引1的位置创建单元格(左上端)
			cell.setCellValue("汇总"); 
			Map<String, Object> linkmap = list.get(0);
			if(linkmap.get("endDate") == null || linkmap.get("beginDate") == null) {
				throw new BizException("请先选择日期！");
			}
			String endDate = linkmap.get("endDate").toString(); 
			String beginDate = linkmap.get("beginDate").toString();
			Date begin = GeneralUtil.StringfromDate(beginDate, "yyyy-MM-dd");
			Date end = GeneralUtil.StringfromDate(endDate, "yyyy-MM-dd");
			Calendar ca = Calendar.getInstance();
			Date beginD = begin;
			int temp = 0;
			while(beginD.compareTo(end) <= 0){
			    ca.setTime(beginD);
			    cell = trow.createCell(temp + 4); // 在索引0的位置创建单元格(左上端)
			    String value = sdf.format(ca.getTime());
				cell.setCellValue(value.toString());
			    ca.add(Calendar.DATE, 1);
			    beginD = ca.getTime();
			    temp ++;
			}
			for (int i = 0; i < list.size(); i++) {
				Row row = sheet.createRow(i + 1);
				Map<String, Object> map = list.get(i);
				cell = row.createCell(0); // 在索引0的位置创建单元格(左上端)
				String sku = map.get("amz_sku") == null ? "-" : map.get("amz_sku").toString();
				cell.setCellValue(sku);
				cell = row.createCell(1); // 在索引1的位置创建单元格(左上端)
				String create = map.get("ownername") == null ? "-" : map.get("ownername").toString();
				cell.setCellValue(create);
				cell = row.createCell(2); 
				
				String openDate = null;;
				if(map.get("openDate")!=null) {
					openDate=map.get("openDate").toString();
					cell.setCellValue(GeneralUtil.formatDate(GeneralUtil.StringfromDate(openDate, "yyyy-MM-dd")));
				}else {
					cell.setCellValue("--");
				}
				cell = row.createCell(3); 
				String vsum = map.get("vsum") == null ? "-" : map.get("vsum").toString();
				cell.setCellValue(vsum);
				int timeTemp = 0;
				Date beginT = begin;
				while(beginT.compareTo(end) <= 0){
				    ca.setTime(beginT);
				    cell = row.createCell(timeTemp + 4);
				    String value = "v" + sdf.format(ca.getTime()).replace("-", "");
				    if(map.get(value) != null && StrUtil.isNotEmpty(map.get(value).toString())){
				    	cell.setCellValue(Double.parseDouble(map.get(value).toString()));
				    }else {
				    	cell.setCellValue(0);
				    }
				    ca.add(Calendar.DATE, 1);
				    beginT = ca.getTime();
				    timeTemp ++;
				}
			}
		}else {
			throw new BizException("没有数据可导出！");
		}
		return workbook;
	}
	
 
	
	public Map<String, Object> getSumOrdersPrice(List<Map<String, Object>> list) {
		Map<String, Object> summap = new HashMap<String, Object>();
		BigDecimal allpricermb = new BigDecimal("0");
		BigDecimal allprice = new BigDecimal("0");
		BigDecimal allqty = new BigDecimal("0");
		BigDecimal allorder = new BigDecimal("0");
		for (Map<String, Object> item : list) {
			allpricermb = allpricermb.add(item.get("orderpricermb")==null?new BigDecimal("0"):new BigDecimal(item.get("orderpricermb").toString()));
			allprice = allprice.add(item.get("orderprice")==null?new BigDecimal("0"):new BigDecimal(item.get("orderprice").toString()));
			allqty = allqty.add(item.get("quantity")==null?new BigDecimal("0"):new BigDecimal(item.get("quantity").toString()));
			allorder = allorder.add(item.get("ordersum")==null?new BigDecimal("0"):new BigDecimal(item.get("ordersum").toString()));
		}
		summap.put("allpricermb", allpricermb);
		summap.put("allqty", allqty);
		summap.put("allorder", allorder);
		summap.put("allprice", allprice);
		summap.put("allcurrency", list.get(0).get("currency"));
		return summap;
	}

	public void getOrdersPriceExport(SXSSFWorkbook workbook, Map<String, Object> paramMap) {
		Sheet sheet = workbook.createSheet("sheet1");
		List<Map<String, Object>> list = ordersSummaryMapper.getOrdersPrice(paramMap);
		Map<String,Object> titleMap = new HashMap<String, Object>();
		titleMap.put("sku", "SKU");
		titleMap.put("fowner", "产品负责人");
		titleMap.put("groupname", "店铺");
		titleMap.put("marketname", "站点");
		titleMap.put("amounttype", "金额分类");
		titleMap.put("amountdescription", "金额描述");
		titleMap.put("amount", "金额");
		titleMap.put("quantity_purchased", "购买数量");
		titleMap.put("currency", "币别");
		if(list==null){
			list = new ArrayList<Map<String,Object>>();
		}
		list.add(0,titleMap);
		
		List<String> titleList = new ArrayList<String>();
		titleList.add("sku");
		titleList.add("fowner");
		titleList.add("groupname");
		titleList.add("marketname");
		titleList.add("amount_type");
		titleList.add("amount_description");
		titleList.add("amount");
		titleList.add("quantity_purchased");
		titleList.add("currency");
		
		for(int i=0;i<list.size();i++) {
			Map<String, Object> map = list.get(i);
			Row row = sheet.createRow(i);
			for(int step = 0; step < titleList.size(); step++) {
				Cell cell = row.createCell(step);
				Object value = map.get(titleList.get(step));
				if(value!=null) {
					cell.setCellValue(value.toString());
				}else {
					cell.setCellValue("--");
				}
			}
		}
		
	}
}
