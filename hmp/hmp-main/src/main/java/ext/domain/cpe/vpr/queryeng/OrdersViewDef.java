package EXT.DOMAIN.cpe.vpr.queryeng;

import static EXT.DOMAIN.cpe.vpr.queryeng.query.QueryDefCriteria.where;
import EXT.DOMAIN.cpe.vpr.Order;
import EXT.DOMAIN.cpe.vpr.queryeng.ColDef.HL7DTMColDef;
import EXT.DOMAIN.cpe.vpr.queryeng.query.JDSQuery;
import EXT.DOMAIN.cpe.vpr.queryeng.query.QueryDef;
import EXT.DOMAIN.cpe.vpr.viewdef.RenderTask;

import java.util.Iterator;
import java.util.Map;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;


@Component(value="EXT.DOMAIN.cpe.vpr.queryeng.OrdersViewDef")
@Scope("prototype")
public class OrdersViewDef extends ViewDef {
	
	public OrdersViewDef() {
		// declare the view parameters
		declareParam(new ViewParam.ViewInfoParam(this, "Orders"));
        declareParam(new ViewParam.PatientIDParam());
		declareParam(new ViewParam.ENUMParam("filter_group", "", "all", "NURS", "CH,MI,LAB"));
		declareParam(new ViewParam.ENUMParam("filter_status", "urn:va:order-status:actv,urn:va:order-status:pend", "all", "urn:va:order-status:actv", "urn:va:order-status:pend", "urn:va:order-status:actv,urn:va:order-status:pend"));
		declareParam(new ViewParam.QuickFilterParam("qfilter_status", "", "ACTIVE", "PENDING", "CANCELLED", "COMPLETE", "DISCONTINUED", "EXPIRED", "LAPSED", "SCHEDULED", "UNRELEASED", "DISCONTINUED/EDIT"));
		declareParam(new ViewParam.AsArrayListParam("filter_group"));
		declareParam(new ViewParam.AsArrayListParam("filter_status"));
		declareParam(new ViewParam.AsArrayListParam("qfilter_status"));
		declareParam(new ViewParam.DateRangeParam("range", null));
		declareParam(new ViewParam.SortParam("start", false));
		
		// list of fields that are not displayable as columns and a default user column set/order
		String displayCols = "Summary,Status,start,ProviderName,Facility,nurseVerify,clerkVerify";
		String requireCols = "Summary,Facility,overallStart,overallStop";
		String hideCols = "uid,selfLink";
		String sortCols = "start,stop,displayGroup";
		String groupCols = "displayGroup,locationName";
		declareParam(new ViewParam.ColumnsParam(this, displayCols, requireCols, hideCols, sortCols, groupCols));

		// Relevant orders
		QueryDef qry = new QueryDef();
		qry.fields().alias("facilityName","Facility").alias("statusName","Status");
		qry.fields().alias("providerName","ProviderName").alias("summary","Summary");
		qry.addCriteria(where("statusCode").in("?:filter_status"));
		qry.addCriteria(where("displayGroup").in("?:filter_group"));
		qry.addCriteria(where("Status").in("?:qfilter_status"));
		Query q1 = new JDSQuery("uid",qry,"vpr/{pid}/index/order?order=#{getParamStr('sort.ORDER_BY')}"+
				"#{getParamStr('range.startHL7')!=null?'&filter=between(start,\"'+getParamStr('range.startHL7')+'\",\"'+getParamStr('range.endHL7')+'\")':''}"){
				//"#{getParamStr('range.startHL7')!=null?'&filter=between(overallStart,\"'+getParamStr('range.startHL7')+'\",\"'+getParamStr('range.endHL7')+'\")':''}"){
				protected Map<String, Object> mapRow(RenderTask renderer, Map<String, Object> row) {
					Map<String, Object> ret = super.mapRow(renderer, row);
					if(ret.containsKey("clinicians") && ret.get("clinicians") instanceof Iterable<?>)
					{
						ret.put("nurseVerify", OrdersViewDef.this.getVerified("N",(Iterable<Map<Object, Object>>)ret.get("clinicians")));
						ret.put("clerkVerify", OrdersViewDef.this.getVerified("C",(Iterable<Map<Object, Object>>)ret.get("clinicians")));
						ret.put("chartVerify", OrdersViewDef.this.getVerified("R",(Iterable<Map<Object, Object>>)ret.get("clinicians")));
					} 
					return ret;
				}
		};
	
		addColumns(q1, "uid", "Summary", "Facility", "locationName", "Status", "displayGroup", "ProviderName", "nurseVerify", "clerkVerify");
        getColumn("Summary").setMetaData("text", "Order").setMetaData("flex", 1);
        getColumn("Summary").setMetaData("flex", 1);
		getColumn("displayGroup").setMetaData("text", "Group");
		getColumn("Status").setMetaData("width", 50);
		getColumn("nurseVerify").setMetaData("text","V.Nurse");
		getColumn("clerkVerify").setMetaData("text","V.Clerk");
		getColumn("ProviderName").setMetaData("text", "Ordering Provider").setMetaData("width", 125);

        addColumn(new HL7DTMColDef(q1, "start")).setMetaData("text", "Start Date").setMetaData("width", 75);
		addColumn(new HL7DTMColDef(q1, "stop")).setMetaData("text", "Stop Date").setMetaData("width", 75);
        getColumn("locationName").setMetaData("text", "Location");

        addColumn(new DomainClassSelfLinkColDef("selfLink", Order.class));
		addQuery(q1);
	}
	
	private String getVerified(String verifyVal, Iterable<Map<Object, Object>> iterable) {
		Iterator<Map<Object, Object>> it = iterable.iterator();
		String rslt = null;
		while(it.hasNext() && rslt == null)
		{
			Map<Object, Object> next = it.next();
			if(next.get("role").toString().equalsIgnoreCase(verifyVal)) // Per Mel, this is sufficient.
			{
				rslt = next.get("name").toString();
			}
		}
		return rslt;
	}
}

