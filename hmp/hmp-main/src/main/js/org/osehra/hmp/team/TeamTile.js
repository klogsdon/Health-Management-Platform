Ext.define('org.osehra.hmp.team.TeamTile', {
    extend:'Ext.grid.Panel',
    requires:[
        'org.osehra.hmp.UserContext',
        'org.osehra.hmp.team.TeamPositionField',
        'org.osehra.hmp.team.PersonField',
        'org.osehra.cpe.roster.RosterPicker'
    ],
    alias:'widget.teamtile',
    ui:'gadget',
    frame:true,
    width:360,
    height:300,
    tbar:[
        {
            xtype:'form',
            defaults:{
                labelSeparator:null
//                labelAlign: 'right'
            },
            items:[
                {
                    xtype:'displayfield',
                    name:'owner',
                    fieldLabel:'Owner'
                },
                {
                    xtype:'rosterpicker',
                    name:'roster',
                    fieldLabel:'Patient List'
                }
            ]
        }
    ],
    columns:[
        {
            text:'Position',
            dataIndex:'position',
            editor:{
                xtype:'teampositionfield'
            }
        },
        {
            text:'Person',
            dataIndex:'person',
            editor:{
                xtype:'personfield',
                valueField:'name',
                displayField:'name'
            },
            flex:1
        }
    ],
    selType:'cellmodel',
    plugins:[
        {
            ptype:'cellediting',
            clicksToEdit:1
        }
    ],
    bbar:[
        {
            icon:'/images/icons/ic_plus.png',
            qtip:'Add Team Position',
            listeners:{
                click:function (button) {
                    var newRecord = { position:'New Position', person:null};
                    button.up('teamtile').getStore().add(newRecord);
                }
            }
        }
    ],
    initComponent:function () {
        var me = this;
        Ext.applyIf(me, {
            store:Ext.create('Ext.data.Store', {
                fields:['position', 'person']
            })
        });

        me.callParent(arguments);

        var owner = me.owner ? me.owner : org.osehra.hmp.UserContext.getUserInfo().userName;
        me.down('form').getForm().setValues({
            owner:owner
        });
    }
});
