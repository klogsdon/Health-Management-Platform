<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="EXT.DOMAIN.cpe.vpr.*"%>
<%@ page import="org.springframework.data.domain.Page"%>

<%-- TODO: wild idea: if this was a Frame with a GSPRenderAction, then some settings etc could become frame params and configurable by an admin interface.--%>
<html>
<head>
    <title>Hgb A1C</title>
    <g:render template="/layouts/detail"/>
</head>

<body>

<div id="chartTargetID" style="float: left;"></div>

<div>
	Pt Hbg A1C goal: <input type="text" value="7.4"> <input type="submit" value="Save Patient Goal">
</div>

<g:javascript>

	var ColumnAreaSeries = Highcharts.extendClass(Highcharts.seriesTypes.column, {
		pointAttrToOptions: { // mapping between SVG attributes and the corresponding options
			stroke: 'lineColor',
			'stroke-width': 'lineWidth',
			fill: 'fillColor',
			'fill-opacity': 'opacity',
			r: 'radius'
		},	
		translate: function () {
			Highcharts.seriesTypes.column.prototype.translate.call(this);
			var xAxis = this.xAxis,
				chart = this.chart;
			
			Highcharts.each(this.points, function(point) {
				point.barW = xAxis.translate(point.x2, 0, 0, 0, 1, false) - point.plotX;
				point.pointWidth = point.barW;
				point.shapeArgs = chart.renderer.Element.prototype.crisp.call(0, point.borderWidth, point.barX, point.barY, point.barW, point.barH);
			});
		}
	});
	Highcharts.seriesTypes.columnarea = ColumnAreaSeries;


	Ext.onReady(function() {
		var cfg = {
			chart: {
				height: 350,
				width: 800,
	            renderTo: 'chartTargetID',
	            zoomType: 'x'
	        },
	        credits: {enabled: false},
	        title: {text: 'A1C%, Goal and Oral Hypoglycemics'},
	        subtitlexx: {
	        	text: '' 
	        },
	        legend: { enabled: false },
	        xAxis: {
	            type: 'datetime',
	            dateTimeLabelFormats: {
	                second: '%m/%d/%Y',
	                minute: '%m/%d/%Y',
	                hour: '%m/%d/%Y',
	                day: '%m/%d/%Y',
	                week: '%m/%d/%Y',
	                month: '%m/%d/%Y',
	                year: '%m/%d/%Y'
	            },
	            labels: {rotation: 45, align: 'left'}
            },
	        yAxis: [{ // A1C, primary
	        	min: 5,
	        	max: 15,
	        	title: {
	        		text: 'A1C%'
        		},
        		labels: {
        			formatter: function() {
        				return this.value + '%';
        			}
        		},
	            plotLinesx: [{
	                value: 0,
	                width: 1,
	                color: '#808080'
	            }],
				plotBands: [{
                    from: 6,
                    to: 7.5,
                    color: 'rgba(68, 170, 213, 0.5)',
                    label: {
                        text: 'A1C Goal Range',
                        style: {
                            color: '#606060'
                        }
                    }
                }]	            
	        },{	// Drug dose, secondary
	        	type: 'logarithmic',
	        	title: {
	        		text: 'Dose'
	        	},
	        	labels: {
	        		enabled: false,
					style: {
                        color: '#4572A7'
                    }
	        	},
	        	style: {
	        		color: '#4572A7'
        		},
        		opposite: true
	        }],
	        series: []
		};
		
		var chart = new Highcharts.Chart(cfg);
		
		
		Ext.Ajax.request({
			url: '/vpr/view/render?view=EXT.DOMAIN.cpe.vpr.queryeng.LabTrendViewDef&range=2000..NOW&row.count=1000&pid=<%=pid%>&filter_typeCodes=urn:lnc:4548-4',
			success: function(resp) {
				var data = Ext.JSON.decode(resp.responseText).data;
				
				var newdata = [];
				for (var i in data) {
					var obs = EXT.DOMAIN.hmp.util.HL7DTMFormatter.UTC(data[i].observed);
					var val = data[i]['0'];
					var err = val * 0.035;
					newdata.push([obs,val]);
					//newdata.push([obs,val-err, val+err]);
				}
				newdata.sort(function(i1, i2){
					if(i1['x']) return i1['x']-i2['x'];
		        	return i1[0] - i2[0];
		        });				
				
				chart.addSeries({name: 'A1C Values', zIndex: 10, type: 'line', data: newdata});
			}
		});	
		
		Ext.Ajax.request({
			url: '/vpr/view/render?view=EXT.DOMAIN.cpe.vpr.queryeng.MedsTabViewDef&range=2000..NOW&row.count=1000&pid=<%=pid%>&filter_class_code=urn:vadc:HS502',
			success: function(resp) {
				var data = Ext.JSON.decode(resp.responseText).data;
				
				for (var i in data) {
					var dosedata = [];
					for (var j in data[i].doseList) {
						var dose = data[i].doseList[j];
						var start = EXT.DOMAIN.hmp.util.HL7DTMFormatter.UTC(dose.doseStart);
						var stop = EXT.DOMAIN.hmp.util.HL7DTMFormatter.UTC(dose.doseStop);
						
						if (dose.doseStart && dose.doseStart) {
							var doseVal = parseFloat(dose.dose.split(' ')[0]);
							// for testing... modifies doses by 25% for visual aid
							/*
							doseVal = doseVal + (j*doseVal*.25);
							console.log(doseVal);
							*/
							dosedata.push({x: start, x2: stop, y:doseVal});
						}
						//break; // only 1 dose for now...
					}
					// sort
					dosedata.sort(function(i1, i2){
						if(i1['x']) return i1['x']-i2['x'];
			        	return i1[0] - i2[0];
			        });
					
					chart.addSeries({id: 'doses', zIndex: 0, type: 'columnarea', lineColor: 'red', lineWidth: 0, fill: 'red', opacity: .2, radius: 0, name: data[i].name, yAxis: 1, data: dosedata});
				}
			}
		});		
		
	});
</g:javascript>

</body>
</html>
