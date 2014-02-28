package org.osehra.cpe.vpr.queryeng.query;

import org.osehra.cpe.jsonc.JsonCCollection;
import org.osehra.cpe.vpr.pom.jds.JdsTemplate;
import org.osehra.cpe.vpr.queryeng.Query;
import org.osehra.cpe.vpr.viewdef.RenderTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JDSQuery fetches raw data/fields from a JDS store (Cache, Mongo, Relational)
 * JDSQuery bypasses the DAO's and Domain objects.  So if utilizing full domain objects
 * is necessary (for al the juicy business logic they might contain), then use {@link DAOQuery} instead.
 */
public class JDSQuery extends Query {
    private QueryDef qry;

    /**
     * Construct a JDS query from the given QueryDef.
     */
    public JDSQuery(String pk, QueryDef qry) {
        super(pk, null);
        this.qry = qry;
    }

    /**
     * Instead of using a query generated by QueryDef, use the specified one instead.
     * <p/>
     *
     * @param pk
     * @param qry
     * @param url
     */
    public JDSQuery(String pk, QueryDef qry, String url) {
        super(pk, url);
        this.qry = qry;
    }
    
    /**
     * For simple queries or queries were you would rather specify your own URL and don't need any 
     * variable interpolation, middle-tier filtering, etc.
     */
    public JDSQuery(String pk, String url) {
        super(pk, url);
    }


    @Override
    public void exec(RenderTask task) throws Exception {
        JdsTemplate tpl = task.getResource(JdsTemplate.class);
        Map<String, Object> params = getParams(task);
        int start = task.getParamInt("row.start");
        int count = task.getParamInt("row.count");

        // build and execute the HTTP request
        String qs = getQueryString();
        String url = (qs != null) ? evalQueryString(task, qs) : null;
        if (url == null) {
            url = qry.toURL(params, start, count);
        } else {
            // ensure start+count are there
            url += (url.indexOf("?") > 0) ? "&" : "?";
            url += String.format("start=%d&limit=%d", start, count);
        }

        // execute the query
        JsonCCollection<Map<String, Object>> response = tpl.getForJsonC(task.evalString(url), params);
        List<Map<String, Object>> items = (response != null) ? response.getItems() : null;
        filterTransformResults(task, params, items);
    }
    
    protected void filterTransformResults(RenderTask task, Map<String, Object> params, List<Map<String, Object>> items) {
        if (items == null) {
            return;
        } else if (qry != null) {
        	// apply middle tier filters, aliases, transformations, etc...
        	qry.applyFilters(items, params);
        }
        
        // add the rows to the task results....
        for (Map<String, Object> item : items) {
        	task.add(mapRow(task, item));
        }
    }

    protected Map<String, Object> getParams(RenderTask task) {
        Map<String, Object> params = new HashMap<String, Object>(task.getParams());

        // if this is running per-row, merge the row data with the params; params take precedence if keys collide
        if (task instanceof RenderTask.RowRenderSubTask) {
            Map<String, Object> row = ((RenderTask.RowRenderSubTask) task).getParentRow();
            for (Map.Entry<String,Object> entry : row.entrySet()) {
                 if (!params.containsKey(entry.getKey())) {
                     params.put(entry.getKey(), entry.getValue());
                 }
            }
        }

        return params;
    }
}