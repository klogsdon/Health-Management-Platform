Ext.define('EXT.DOMAIN.cpe.viewdef.LabGridAdvisor', {
    extend: 'EXT.DOMAIN.cpe.viewdef.GridAdvisor',
    tpl: new Ext.XTemplate('{result} {units} <div style=\"float: right; color: red; font-weight: bold;\">{interpretation}</div>', {compiled: true}),
    defineColumns: function(grid, metadata) {
        return [
            {text: 'Result Name', dataIndex: 'name', width: 200, menuDisabled: true},
            {text: 'Value', xtype: 'templatecolumn', width: 90, tpl: this.tpl, resizable: false}
        ]
    }
});
