package org.osehra.cpe.vpr.queryeng;

import org.osehra.cpe.datetime.PointInTime;
import org.osehra.cpe.datetime.format.HL7DateTimeFormat;
import org.osehra.cpe.vista.springframework.security.userdetails.VistaUserDetails;
import org.osehra.cpe.vpr.viewdef.RenderTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.codehaus.groovy.grails.web.json.JSONArray;
import org.codehaus.groovy.grails.web.json.JSONObject;
import org.joda.time.Period;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;

/**
ViewParams have 0+ properties (key/value pairs)
ViewParams might have GUI counterparts (like a date/time picker)
ViewParams can validate themselves (required/range+bounds checking/etc)

A simple ViewParam might be something like: DateParam, NumberParam, etc.
A complex ViewParam might be something like: VocabHierarchyParam, etc.

A ViewParam might act as a container of other ViewParams (helps rendering, etc.)

ViewParam could be evaluated statically, or dynamically.

ViewParams should not store user state, they are mearly metadata so they can retrieve and/or generate
the current value (which will then be stored in the ViewRenderer hopefully.

Some VIewParams assist in generating queries (filters, pid, etc.),
Some ViewParams are generated from queries (QueryColumnListParam, etc.)
Some VIewParams might be for display only, like DateFormatting, column show/hide, etc.

Some ViewParams might have associated editors in ExtJS
ViewParams might have metadata like required, display names, allowable value ranges, etc.

ViewParams determine if a property is visible in param editors, or required to be passed in on urls.

TODO: evaluate needs to be able to emmit multiple params

10/14/2011:
ViewParams are small containers of params (key/value pairs)
VIewParams group and categorize params
VIewParams have metadata to assist in designing view editors
ViewParams are self validating 
ViewParams hold related key/value pairs that may be persisted, specified (url vars) or calculated/derived, or all of the above.
ViewParams can be dynamically re-evaluated every time.
ViewParams shouldn't store any 'effective' or calculated values in order to be threadsafe.

DynamicParameters Options:
// 1) should I map all the keys to their ViewParam class that generates the values each time?
// 2) should I ahave a DYNAMIC flag that indicates recalc each time (also requires a key to ViewParam mapping)
// 3) dynamic flag means the stored value is another object (maybe another ViewParam wrapper class) which is evaluated/
// *** 4) recalc params after key events (query run, initialization, etc.)
// 5) keep a list of just the dynamic param keys, so we know if it gets called (or when a getParamMap()) then calc on the fly?		

TODO: Would be cool to make this (or a subclass) generic and add a T getValue(RenderContentext) method
to take care of casting for you.  Then the param would be declared as a class member and used to fetch the value. 

TODO: Rename to FrameParam?

*/
public abstract class ViewParam {
	
	private Map<String, Object> metadata = new HashMap<String,Object>();
	
	public ViewParam() {
	}
	
	public boolean validate(RenderTask renderer) throws IllegalStateException {
		return true;
	}
	
	/****************** metadata methods *************/
	
	public Map<String, Object> getMetaData(RenderTask task) {
		Map<String, Object> ret = new HashMap<String, Object>();
		// always put the class name and the default values
		ret.put("clazz", this.getClass().getName());
		ret.put("defaults", getDefaultValues());
		ret.put("values", calcParams(task));
		// then add anything else declared and return it
		ret.putAll(metadata);
		return ret;
	}
	
	public ViewParam addMeta(String key, Object val) {
		this.metadata.put(key, val);
		return this;
	}
	
	public ViewParam addMeta(Map<String,Object> map) {
		this.metadata.putAll(map);
		return this;
	}

	/***************** value methods ******************/
	public abstract Map<String, Object> getDefaultValues();
	public Map<String, Object> calcParams(RenderTask task) {
		Map<String, Object> ret = getDefaultValues();
		if (ret == null) {
			return new HashMap<String, Object>();
		}
		return ret;
	}
	
	public static class SimpleViewParam extends ViewParam {
		protected String key;
		protected Object val;
		
		public SimpleViewParam(String key) {
			this(key, null);
		}
		
		public SimpleViewParam(String key, Object defaultVal) {
			this.key = key;
			this.val = defaultVal;
			addMeta("key", key);
		}

		@Override
		public Map<String, Object> getDefaultValues() {
			HashMap<String, Object> ret = new HashMap<String, Object>();
			ret.put(this.key, this.val);
			return ret;
		}
		
		@Override
		public Map<String, Object> calcParams(RenderTask task) {
			Map<String, Object> ret = new HashMap<String, Object>();
			if (task.getParamObj(this.key) != null) {
				ret.put(this.key, task.getParamObj(this.key));
			} else {
				ret.put(this.key, this.val);
			}
			return ret;
		}
	}
	
	/**
	 * Trying to consolodate on PID being the primary patient id parameters.
	 * 
	 * By declaring this parameter in your view def, you are essentially declaring it patient-specific.
	 * 
	 * Previously we had lots of inconsistency with pid, patient_id, patient.id, which is now consolodated to pid. 
	 * 
	 * Its now required and the only value recognized, but for compatability, its aliased to patient_id and patient.id
	 */
	public static class PatientIDParam extends ViewParam {
		@Override
		public Map<String, Object> getDefaultValues() {
			// patient ID can't have a default value
			return null;
		}
		
		@Override
		public Map<String, Object> calcParams(RenderTask renderer) {
			Map<String, Object> ret = super.calcParams(renderer);
			ret.put("patient_id", renderer.getParamStr("pid"));
			ret.put("patient.id", renderer.getParamStr("pid"));
			return ret;
		}
		
		@Override
		public boolean validate(RenderTask renderer) throws IllegalStateException {
			if (renderer.getParamStr("pid") == null) {
				throw new IllegalArgumentException("'pid' parameter is required");
			}
			return super.validate(renderer);
		}
	}
	
	public static class ColumnValuesArrayParam extends ViewParam {
		protected String colkey;
		protected Query q;
		protected String key;

		public ColumnValuesArrayParam(String key, Query q, String colkey) {
			this.key = key;
			this.q = q;
			this.colkey = colkey;
		}

		public Set<String> getKeys() {
			HashSet<String> ret = new HashSet<String>();
			ret.add(key);
			return ret;
		}
		
		@Override
		public Map<String, Object> getDefaultValues() {
			// this has no defaults
			return null;
		}

		@Override
		public Map<String, Object> calcParams(RenderTask renderer) {
			Map<String, Object> ret = super.calcParams(renderer);
			ret.put(key, new ArrayList(renderer.getColumnValues(this.colkey)));
			return ret;
		}
	}
	
	public static class ColumnValuesListParam extends ColumnValuesArrayParam {
		private String delimchar;
		private String quotechar;

		public ColumnValuesListParam(String key, Query q, String colkey) {
			this(key, q, colkey, ",", "'");
		}
		
		public ColumnValuesListParam(String key, Query q, String colkey, String delimchar) {
			this(key, q, colkey, delimchar, "'");
		}

		public ColumnValuesListParam(String key, Query q, String colkey, String delimchar, String quotechar) {
			super(key, q, colkey);
			this.delimchar = delimchar;
			this.quotechar = quotechar;
		}
		
		@Override
		public Map<String, Object> calcParams(RenderTask renderer) {
			Map<String, Object> ret = super.calcParams(renderer);
			Object obj = ret.get(key);
			if (obj instanceof List) {
				ret.put(key, join((List) obj, this.delimchar, this.quotechar));
			} else if (obj instanceof Object[]) {
				ret.put(key, join((Object[]) obj, this.delimchar, this.quotechar));
			}
			return ret;
		}
		
		public static String join(List l, String delim, String quote) {
			if (l == null || l.size() == 0) {
				return "";
			}
			StringBuffer sb = new StringBuffer();
			for (Object o : l) {
				if (o == null) {
					continue;
				} else if (sb.length() > 0) {
					// use a delimiter unless its the first item in the list					
					sb.append(delim);
				}
				// quoted value
				sb.append(quote + o + quote);
			}
			return sb.toString();
		}
		
		public static String join(Object[] l, String delim, String quote) {
			if (l == null || l.length == 0) {
				return "";
			}
			StringBuffer sb = new StringBuffer();
			for (Object o : l) {
				if (o == null) {
					continue;
				} else if (sb.length() > 0) {
					// use a delimiter unless its the first item in the list					
					sb.append(delim);
				}
				// quoted value
				sb.append(quote + o + quote);
			}
			return sb.toString();
		}
	}
	
	/**
	 * Converts the value to an array list, no matter if the input is a CSL list, java.util.List or an Array. 
	 */
	public static class AsArrayListParam extends ViewParam {
		private String key;
		private String srckey;
		
		public AsArrayListParam(String key) {
			this.srckey = this.key = key;
		}
		
		public AsArrayListParam(String targetkey, String srckey) {
			this.key = targetkey;
			this.srckey = srckey;
		}
		
		
		@Override
		public Map<String, Object> getDefaultValues() {
			return null;
		}

		@Override
		public Map<String, Object> calcParams(RenderTask renderer) {
			Map<String, Object> ret = super.calcParams(renderer);
			ret.put(key, toArrayList(renderer.getParamObj(srckey)));
			return ret;
		}
		
		public static final ArrayList<?> toArrayList(Object obj) {
			if (obj != null && obj instanceof String) {
				ArrayList<Object> ret = new ArrayList<Object>();
				StringTokenizer st = new StringTokenizer(obj.toString(), ",");
				while (st.hasMoreTokens()) {
					ret.add(st.nextToken().trim());
				}
				return ret;
			} else if (obj != null && obj instanceof Object[]) {
				ArrayList<Object> tmp = new ArrayList<Object>();
				for (Object o : (Object[]) obj) {
					tmp.add(o);
				}
				return tmp;
			} else {
				return new ArrayList<Object>();
			}
		}
	}
	
	/*
	 * This param controls the following key/value properties
	 * row.start= the first row to render
	 * row.count= how many rows to display per page?
	 * row.limit= hard limit # of rows to deal with
	 * 
	 * TODO: row.limit <= 0 means unlimited?
	 * TODO: have an auto-caculated page#? floor(row.start / row.count)
	 */
	public static class PaginationParam extends ViewParam {
		public PaginationParam() {
		}
		
		@Override
		public Map<String, Object> getDefaultValues() {
			HashMap<String, Object> ret = new HashMap<String, Object>();
			ret.put("row.start", 0);
			ret.put("row.count", 25);
			ret.put("row.limit", 1000);
			return ret;
		}
		
		@Override
		public boolean equals(Object obj) {
			// only one instance can be added, so treat all of these as equal
			return obj instanceof PaginationParam;
		}
		
		@Override
		public Map<String, Object> calcParams(RenderTask task) {
			Map<String, Object> ret = super.calcParams(task);
			
			String[] keys = new String[] {"row.start", "row.count", "row.limit"};
			for (String s : keys) {
				if (task.getParamObj(s) != null) {
					ret.put(s, task.getParamInt(s));
				}
			}
				
			return ret;
		}
	}
	
	/*
	 * This parameter knows how to generate an SQL where clause by interprenting the
	 * various params generated by ExtJS (group, sort, etc.)
	 * TODO: How to click sort on one column, but map it to sort on another column (ex: pt_name combo sorts by last name)
	 */
	public static class SortParam extends ViewParam {
		private boolean dir;
		private String col;
		private String colprefix;
		
		public SortParam(String col, boolean asc) {
			this(col, asc, null);
		}
		
		public SortParam(String col, boolean asc, String colPrefix) {
			this.col = col;
			this.dir = asc;
			this.colprefix = colPrefix;
		}
		
		@Override
		public Map<String, Object> calcParams(RenderTask renderer) {
			Map<String, Object> ret = super.calcParams(renderer);

			String orderBy = "";
			ArrayList<String> sortFields = new ArrayList<String>();
			ArrayList<Integer> sortOrders = new ArrayList<Integer>();
			
			// generate SQL ORDER BY clause (start with the group parameter, if any)
			Object group = renderer.getParamObj("group");
			if (group != null && group instanceof JSONArray) {
				JSONArray sortary = (JSONArray) group;
				for (int i=0; i < sortary.length(); i++) {
					JSONObject obj = (JSONObject) sortary.get(i);
					if (orderBy.length() != 0) {
						orderBy += ", ";
					}
					orderBy += (this.colprefix != null ? this.colprefix : "") + obj.getString("property") + " " + obj.getString("direction");
					sortFields.add(obj.getString("property"));
					sortOrders.add(obj.getString("direction").equalsIgnoreCase("DESC") ? -1 : 1);
				}
			}
			
			// append to the SQL ORDER BY clause (with anythingn from the sort parameter, if any)
			Object sort = renderer.getParamObj("sort");
			if (sort != null && sort instanceof JSONArray) {
				JSONArray sortary = (JSONArray) sort;
				for (int i=0; i < sortary.length(); i++) {
					JSONObject obj = (JSONObject) sortary.get(i);
					if (orderBy.length() != 0) {
						orderBy += ", ";
					}
					orderBy += (this.colprefix != null ? this.colprefix : "") + obj.getString("property") + " " + obj.getString("direction");
					sortFields.add(obj.getString("property"));
					sortOrders.add(obj.getString("direction").equalsIgnoreCase("DESC") ? -1 : 1);
				}
			}
			
			// if orderBy is still empty, use the defaults
			if (orderBy.length() == 0) {
				orderBy = (this.colprefix != null ? this.colprefix : "") + this.col + " " + (this.dir ? "ASC" : "DESC");
				sortFields.add(this.col);
				sortOrders.add(this.dir ? 1 : -1);

			}
			
			// calculate the final values to return
			ret.put("sort.col", sortFields.get(0));
			ret.put("sort.dir", sortOrders.get(0) == 1 ? "ASC" : "DESC");
			ret.put("sort.ORDER_BY", orderBy);
			ret.put("sort.fields", sortFields);
			ret.put("sort.orders", sortOrders);
			return ret;
		}
		
		@Override
		public Map<String, Object> getDefaultValues() {
			HashMap<String, Object> ret = new HashMap<String, Object>();
			ret.put("sort.col", this.col);
			ret.put("sort.dir", this.dir ? "ASC" : "DESC");
			return ret;
		}
	}

	public static class ColumnsParam extends ViewParam {
		private String disp;
		private String req;
		private String hide;
		private String sort;
		private String group;
		private ViewDef view;

		public ColumnsParam(ViewDef view) {
			this.view = view;
		}
		
		public ColumnsParam(ViewDef view, String displayCols, String requireCols, String hideCols, String sortCols, String groupCols) {
			this.view = view;
			disp = displayCols;
			req = requireCols;
			hide = hideCols;
			sort = sortCols;
			group = groupCols;
		}
		
		@Override
		public Map<String, Object> getDefaultValues() {
			HashMap<String, Object> ret = new HashMap<String, Object>();

			// build a list of all declared columns
            List<String> colKeys = new ArrayList<String>(this.view.getColumns().size());
            for (ColDef col : this.view.getColumns()) {
                colKeys.add(col.getKey());
            }
			String colList = StringUtils.collectionToCommaDelimitedString(colKeys);
			
			// if the display list is null (not defined) then show them all
			ret.put("col.display", this.disp == null ? colList : this.disp);
			ret.put("col.require", this.req == null ? "" : this.req);
			ret.put("col.suppress", this.hide == null ? "" : this.hide);
			ret.put("col.sortable", this.sort == null ? "" : this.sort);
			ret.put("col.groupable", this.group == null ? "" : this.group);
			ret.put("col.list", colList);
			
			return ret;
		} 
		
		@Override
		public boolean equals(Object obj) {
			// only one instance can be added, so treat all of these as equal
			return obj instanceof ColumnsParam;
		}
	}
	
	public static class ViewInfoParam extends ViewParam {
		private ViewDef vd;
		private String name;
		private String[] domainClasses;
		private String[] detailLinkFields;
		
		public ViewInfoParam(ViewDef vd) {
			this(vd, null, null, null);
		}
		
		public ViewInfoParam(ViewDef vd, String name) {
			this(vd, name, null, null);
		}
		
		public ViewInfoParam(ViewDef vd, String name, String[] domainClasses) {
			this(vd, name, domainClasses, null);
		}
		
		public ViewInfoParam(ViewDef vd, String name, String[] domainClasses, String[] detailLinkFields) {
			this.vd = vd;
			this.name = name;
			this.domainClasses = domainClasses;
			this.detailLinkFields = detailLinkFields;
			
			// get the annotation, use it to fill in any values not declared in the param
			HMPAppInfo annotation = vd.getClass().getAnnotation(HMPAppInfo.class);
			if (this.name == null && annotation != null) {
				this.name = annotation.title();
			}
		}
		
		public Map<String, Object> getDefaultValues() {
			HashMap<String, Object> ret = new HashMap<String, Object>();
			ret.put("view.class", vd.getClass().getName());
			ret.put("view.name", name != null ? name : vd.getClass().getName());
			ret.put("view.domains", domainClasses);
			ret.put("view.details", detailLinkFields);
			return ret;
		}
		
		@Override
		public boolean equals(Object obj) {
			// only one instance can be added, so treat all of these as equal
			return obj instanceof ViewInfoParam;
		}
	}
	
	public abstract static class ContainerParam extends ViewParam {
		// TODO:The idea here would be that this is some sort of container of other params.
		// might be useful for grouping other parameters together under one GUI/editing group?
	}
	
	public static class ENUMParam extends SimpleViewParam {
		public ENUMParam(String key, String defaultVal, String... enums) {
			super(key, defaultVal);
			addMeta("enum", enums);
			addMeta("multiple", false);
		}
		public ENUMParam(String key, String defaultVal, Vector<String> enums) {
			super(key, defaultVal);
			String[] ery =  new String[enums.size()];
			for(int i = 0; i<enums.size(); i++)
			{
				ery[i] = enums.get(i);
			}
			addMeta("enum", ery);
			addMeta("multiple", false);
		}
		// TODO: Validate should check that its in the ENUM values list.
	}

	/**
	 * TODO: it would be really cool if this could utilize the Terminology engine to help
	 * render the enumerated values into nice display names
	 */
	public static class QuickFilterParam extends SimpleViewParam {
		public QuickFilterParam(String key, String defaultVal, String... enums) {
			super(key, defaultVal);
			addMeta("enum", enums);
		}
		public QuickFilterParam(String key, String defaultVal, Vector<String> enums) {
			super(key, defaultVal);
			String[] ery =  new String[enums.size()];
			for(int i = 0; i<enums.size(); i++)
			{
				ery[i] = enums.get(i);
			}
			addMeta("enum", ery);
		}
	}
	
	public static class NumRangeParam extends ViewParam {
		String key;
		int defaultval;
		public NumRangeParam(String key, int defaultVal, int min, int max) {
			super();
			this.key = key;
			this.defaultval = defaultVal;
			addMeta("key", key);
			addMeta("min", min);
			addMeta("max", max);
		}
		
		@Override
		public Map<String, Object> getDefaultValues() {
			HashMap<String, Object> ret = new HashMap<String, Object>();
			ret.put(this.key, this.defaultval);
			return ret;
		}
		
		public Map<String, Object> calcParams(RenderTask renderer) {
			Map<String, Object> ret = super.calcParams(renderer);
			Object obj = renderer.getParamObj(key);
			
			if (obj != null && obj instanceof Integer) {
				// its already a number
				ret.put(key, (Integer) obj);
			} else if (obj != null) {
				try {
					int val = Integer.parseInt(obj.toString());
					ret.put(key, val);
				} catch (NumberFormatException ex) {
					// its not a number... ignore?
				}
			}
			return ret;
		}
		// TODO: Validate should check that number is in range.
	}

	public static class BooleanParam extends SimpleViewParam {
		public BooleanParam(String key, boolean defaultVal) {
			super(key, defaultVal);
		}
		
		@Override
		public Map<String, Object> calcParams(RenderTask renderer) {
			Map<String, Object> ret = super.calcParams(renderer);
			Object obj = renderer.getParamObj(key);
			if (obj != null && obj instanceof String) {
				String str = obj.toString().toLowerCase();
				if (str.startsWith("f") || str.equals("0")) {
					ret.put(key, false);
				} else if (str.startsWith("t") || str.equals("1")) {
					ret.put(key, true);
				}
			}
			return ret;
		}
	}
	
	public static class DateRangeParam extends ViewParam {
		// Date specifier: T-365D, Today+24H, Now-30, etc.
		private static Pattern PAT = Pattern.compile("([0-9]{4,14}|[a-zA-Z]*)([\\-+ ]?)(\\d*)([a-zA-Z]*)");
		
		private String key;
		private String defaultVal;

		public DateRangeParam(String key, String defaultVal) {
			this.key = key;
			this.defaultVal = defaultVal;
			addMeta("key", key);
			addMeta("title", "Date Range");
		}
		
		@Override
		public Map<String, Object> calcParams(RenderTask task) {
			String value = task.getParamStr(key);
			return parseDate(this.key, (value == null) ? this.defaultVal : value);
		}
		
		public Map<String, Object> getDefaultValues() {
			return parseDate(this.key, this.defaultVal);
		}
		
		protected static Map<String, Object> parseDate(String prefix, String value) {
			if(value==null || value.trim().equals("")) {return null;}
			PointInTime now = PointInTime.now(), today = PointInTime.today(), d1 = parseDateStr(value, today), d2 = now;
			PointInTime start = null;
			PointInTime end = null;
			if(d1!=null) {
				if(d1.compareTo(d2)>0) {
					start = d2;
					end = d1;
				} else {
					start = d1;
					end = d2;
				}
			}
			
			// if parseDateStr did not parse it, try to recognize a few other patterns
			if (start == null) {
				value = (value == null) ? null : value.toLowerCase();
				if (value.contains(":")) {
					// start/end range
					String[] parts = value.split(":");
					start = parseDateStr(parts[0], today);
					end = parseDateStr(parts[1], now);
				} else if (value.contains("..")) {
					// start/end range
					String[] parts = value.split("\\.\\.");
					start = parseDateStr(parts[0], today);
					end = parseDateStr(parts[1], now);
					
				// TODO: the rest of these are some older cases, probably should be removed
					
				} else if (value.equalsIgnoreCase("One Week")) {
					start = new PointInTime(today.subtractDays(7));
					end = today;
				} else if (value.equalsIgnoreCase("One Month")) {
					start = new PointInTime(today.subtractMonths(1));
					end = today;
				} else if (value.equalsIgnoreCase("One Year")) {
					start = new PointInTime(today.subtractYears(1));
					end = today;
				} else if (value.toLowerCase().endsWith("year") || value.toLowerCase().endsWith("years")) {
					String s = value.substring(0, value.indexOf("year")).trim();
					int n = Integer.parseInt(s);
					start = new PointInTime(today.subtractYears(n));
					end = today;
				} 
				
				if (start == null || end == null) {
					throw new RuntimeException("Unknown date format/range: " + value);
				}
			}
			
			// conver to map of results (object + string representations)
			Map<String, Object> ret = new HashMap<String, Object>();
			ret.put(prefix + ".orig", value);
			ret.put(prefix + ".startHL7", start.toString());
			ret.put(prefix + ".endHL7", end.toString());
			
			// A SQL between clause
			if (end == now) {
				ret.put(prefix + ".SQL_BETWEEN", " >= '" + start.toString() + "' ");
			} else {
				ret.put(prefix + ".SQL_BETWEEN", " BETWEEN '" + start.toString() + "' AND '" + end.toString() + "' ");
			}
			
			return ret;
		}
		
		/*
		 * Primary pattern matching, matches the start/end of a range sperately.
		 * Recognizes days, weeks, months, years, hours, minutes, seconds
		 * Minutes and months are case sensitive (big M for month, small m for minute)
		 * TODO: Support for recursive? (IE: +1Y6M == 1.5 years?)
		 */
		public static PointInTime parseDateStr(String value, PointInTime dtm) {
			if (value == null || value.equals("")) {
				return null;
			}
			
			Matcher match = PAT.matcher(value);
			if (match.matches()) {
				
				// start can be now or today (time or no time)
				String startAt = match.group(1);
				if (dtm != null && (startAt == null || startAt.length() == 0)) {
					// use the dtm passed in as the start value
				} else if (startAt == null || startAt.equals("") || startAt.toLowerCase().startsWith("t")) {
					// otherwise use today if there was no start date specified
					dtm = PointInTime.today();
				} else if (startAt.toLowerCase().startsWith("n")) {
					// use now if it was specified
					dtm = PointInTime.now();
				} else if (startAt.matches("\\d{4,14}")) {
					dtm = HL7DateTimeFormat.parse(startAt);
				} else {
					return null; // invalid/unknown start value
				}
				
				// parse the period unit/number (if it exists)
				int num = 0;
				String numstr = match.group(3);
				if (numstr != null && !numstr.equals("")) {
					num = Integer.parseInt(numstr);
				}
				String unit = match.group(4);
				Period p;
				if (unit == null || unit.equals("") || unit.startsWith("d") || unit.startsWith("D")) {
					p = Period.days(num);
				} else if (unit.startsWith("w") || unit.startsWith("W")) {
					p = Period.days(num*7); // Period.weeks() doesn't seem to work!?!
				} else if (unit.startsWith("mo") || unit.startsWith("MO") || unit.equals("M")) {
					p = Period.months(num);
				} else if (unit.startsWith("y") || unit.startsWith("Y")) {
					p = Period.years(num);
				} else if (unit.startsWith("h") || unit.startsWith("H")) {
					p = Period.hours(num);
				} else if (unit.startsWith("mi") || unit.startsWith("MI") || unit.equals("m")) {
					p = Period.minutes(num);
				} else if (unit.startsWith("s") || unit.startsWith("S")) {
					p = Period.seconds(num);
				} else {
					// unrecognized period/units
					return null;
				}
				
				// operator can be +, -, <space> (same as +)
				String op = match.group(2);
				if (op.equals("-")) {
					return dtm.subtract(p);
				} else {
					return dtm.add(p);
				}
			}
			
			return null;
		}
	}
	
	public static class SessionParams extends ViewParam {
		
	    private VistaUserDetails getUserDetails() {
	        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
	        if (auth != null && auth.getPrincipal() instanceof VistaUserDetails) {
	        	return (VistaUserDetails) auth.getPrincipal();
	        }
	        return null;
	    }

		@Override
		public Map<String, Object> getDefaultValues() {
			Map<String, Object> ret = new HashMap<String, Object>();
			ret.put("vista_id", null);
			ret.put("vista_duz", null);
			ret.put("vista_division", null);
			return ret;
		}
		
		@Override
		public Map<String, Object> calcParams(RenderTask renderer) {
			VistaUserDetails user = getUserDetails();
			Map<String, Object> ret = new HashMap<String, Object>();
			if (user != null) {
				ret.put("vista_id", user.getVistaId());
				ret.put("vista_duz", user.getDUZ());
				ret.put("vista_division", user.getDivision());
			}
			return ret;
		}
	}
	
	/*
	 * Converts a list/array into a OR clause with field name 
	 */
	public static class ORedListParam extends ViewParam {
	    private String fieldName;
		private String key;

		public ORedListParam(String key, String fieldName) {
			this.key = key;
			this.fieldName = fieldName;
		}
		
		@Override
		public Map<String, Object> getDefaultValues() {
			return null; // has no default
		}
		
		@Override
		public Map<String, Object> calcParams(RenderTask renderer) {
			Map<String, Object> ret = super.calcParams(renderer);
			List list = AsArrayListParam.toArrayList(renderer.getParamObj(key));
			ret.put(key, splitParamObjFilter(list, this.fieldName));
			return ret;
		}
		
		public String splitParamObjFilter(List<Object> val, String field)
	    {
	    	String rslt = "";
	    	if(val != null && val.size()>0)
	    	{
	    		rslt = " AND (";
	    		List<Object> vl = (List<Object>)val;
	    		for(Object o: vl)
	    		{
	    			if(!(o==vl.get(0)))				
	    			{
	    				rslt = rslt + " OR ";
	    			}
	    			rslt = rslt + field + ":\"" + o + "\" "; 
	    		}
	    		rslt = rslt + ")";
	    	}
	    	return rslt;
	    }
	}
	
	/**
	 * Mark one or more keys as required.  Will throw an error if they are not defined.
	 * 
	 * @author brian
	 */
	public static class RequiredParam extends ViewParam {
		private String[] keys;
		private String msg;
		
		public RequiredParam(String... keys) {
			this.keys = keys;
		}
		
		public RequiredParam(String key, String msg) {
			this.keys = new String[] {key};
			this.msg = msg;
		}
		
		@Override
		public Map<String, Object> getDefaultValues() {
			return null;
		}
		
		@Override
		public Map<String, Object> calcParams(RenderTask task) {
			Map<String, Object> params = task.getParams();
			for (String key : keys) {
				if (!params.containsKey(key)) {
					String msg = this.msg;
					if (msg == null) {
						msg = "Frame: " + task.getParentTask().getFrame().getID() + " requires param: " + key;
					}
					throw new IllegalArgumentException(msg);
				}
			}
			
			return super.calcParams(task);
		}
		
	}
}
