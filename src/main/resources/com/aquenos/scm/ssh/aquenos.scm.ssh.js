/*
 * Copyright 2012 aquenos GmbH.
 * All rights reserved.
 * 
 * This program and the accompanying materials are made available under the 
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html.
 */

Ext.ns('Aquenos.scm.ssh');

Aquenos.scm.ssh.SshKeysPropertyName = "com.aquenos.scm.ssh.authorizedkeys";

// Extend textarea component with support for disabling word wrapping.
// This idea is from "cginzel"
// (http://www.sencha.com/forum/showthread.php?52122).
Ext.override(Ext.form.TextArea, {
  initComponent: Ext.form.TextArea.prototype.initComponent.createSequence(function() {
    Ext.applyIf(this, {
      wordWrap: true
    });
  }),

  onRender: Ext.form.TextArea.prototype.onRender.createSequence(function(ct, position) {
    this.el.setOverflow('auto');
    if (this.wordWrap === false) {
      if (!Ext.isIE) {
        this.el.set({
          wrap: 'off'
        })
      } else {
        this.el.dom.wrap = 'off';
      }
    }
    if (this.preventScrollbars === true) {
      this.el.setStyle('overflow', 'hidden');
    }
  })
});

// The navigation sections do only update the links when rendered first.
// Therefore we have to override the doLayout function in order to make it
// possible to add links after the navigation has been created initially.
Ext.override(Sonia.navigation.NavSection, {
  doLayout: function(shallow, force) {
    Sonia.navigation.NavSection.superclass.doLayout.call(this, shallow, force);
    // Render link itmes
    this.tpl.overwrite(this.body, {
      links: this.links
    });
  }
});

// There is no hook to extend the user form, thus we modify the original class.
Ext.override(Sonia.user.FormPanel, {

  sshKeysText: 'SSH Public Keys',
  sshKeysHelpText: 'SSH public keys for the user in OpenSSH format (one key per line)',

  originalInitComponent: Sonia.user.FormPanel.prototype.initComponent,

  initComponent: function() {
    this.originalInitComponent.apply(this, arguments);
    this.add({
      id: 'userSshKeys',
      xtype: 'textarea',
      fieldLabel: this.sshKeysText,
      name: 'sshKeys',
      helpText: this.sshKeysHelpText,
      autoScroll: true,
      height: 150,
      wordWrap: false
    });

    // Run load data again, so that the sshKeys field is initialized.
    if (this.item != null) {
      this.loadData(this.item);
    }
  },

  removeSshKeysFromItem: function(item) {
    var newItem = Ext.apply({}, item);
    if (newItem.sshKeys != null) {
      var property = {
        key: Aquenos.scm.ssh.SshKeysPropertyName,
        value: newItem.sshKeys
      };
      if (newItem.properties) {
        // Clone properties
        newItem.properties = [];
        var replacedProperty = false;
        Ext.each(item.properties, function(iProperty, i) {
          if (iProperty.key != null && iProperty.key == property.key) {
            if (!replacedProperty) {
              newItem.properties.push(property);
              replacedProperty = true;
            }
          } else {
            newItem.properties.push(iProperty);
          }
        });
        if (!replacedProperty) {
          newItem.properties.push(property);
        }
      } else {
        newItem.properties = [ property ];
      }
      delete newItem.sshKeys;
    }
    return newItem;
  },

  addSshKeysToItem: function(item) {
    // Clone item
    var newItem = Ext.apply({}, item);
    if (newItem.properties != null && Ext.isArray(newItem.properties)) {
      // Clone array
      newItem.properties = [];
      for ( var i = 0; i < item.properties.length; i++) {
        if (item.properties[i].key != null && item.properties[i].value != null
          && item.properties[i].key == Aquenos.scm.ssh.SshKeysPropertyName) {
          newItem.sshKeys = item.properties[i].value;
        } else {
          newItem.properties.push(item.properties[i]);
        }
      }
    }
    return newItem;
  },

  originalLoadData: Sonia.user.FormPanel.prototype.loadData,

  loadData: function(item) {
    var itemWithSshKeys = this.addSshKeysToItem(item);
    this.originalLoadData.call(this, itemWithSshKeys);
  },

  originalUpdate: Sonia.user.FormPanel.prototype.update,

  update: function(item) {
    var itemWithProperty = this.removeSshKeysFromItem(item);
    this.originalUpdate.call(this, itemWithProperty);
  },

  originalCreate: Sonia.user.FormPanel.prototype.create,

  create: function(item) {
    var itemWithProperty = this.removeSshKeysFromItem(item);
    this.originalCreate.call(this, itemWithProperty);
  },

  originalFixRequest: Sonia.user.FormPanel.prototype.fixRequest,

  fixRequest: function(item) {
    if (item.sshKeys !== undefined) {
      delete item.sshKeys;
    }
    this.originalFixRequest.call(this, item);
  }

});

// Window for changing a user's SSH keys (self-service)
Aquenos.scm.ssh.EditMySshKeysWindow = Ext.extend(Ext.Window, {

  linkText: 'Edit My SSH Keys',
  titleText: 'Edit My SSH Keys',
  sshKeysText: 'SSH Public Keys',
  sshKeysHelpText: 'SSH public keys for the user in OpenSSH format (one key per line)',
  okText: 'OK',
  cancelText: 'Cancel',
  errorTitleText: 'Communication Error',
  errorMessageText: 'The communication with the server failed.',
  loadingText: 'Loading...',
  url: restUrl + 'scm-ssh-plugin/my-ssh-keys',

  initComponent: function() {
    var config = {
      layout: 'fit',
      width: 500,
      height: 250,
      closeable: false,
      resizable: false,
      plain: true,
      border: false,
      modal: true,
      title: this.titleText,
      items: [ {
        id: 'editMySshKeysForm',
        url: restUrl + 'scm-ssh-plugin/my-ssh-keys',
        frame: true,
        xtype: 'form',
        monitorValid: 'true',
        defaultType: 'textfield',
        items: [ {
          id: 'editMySshKeysTextArea',
          name: 'sshKeys',
          fieldLabel: this.sshKeysText,
          xtype: 'textarea',
          helpText: this.sshKeysHelpText,
          autoScroll: true,
          height: 150,
          width: 300,
          wordWrap: false
        } ],
        buttons: [ {
          text: this.okText,
          formBind: true,
          scope: this,
          handler: this.saveSshKeys
        }, {
          text: this.cancelText,
          scope: this,
          handler: this.cancel
        } ]
      } ],
      listeners: {
        render: function() {
          this.onLoad(this.el);
        }
      }
    };

    Ext.apply(this, Ext.apply(this.initialConfig, config));
    Aquenos.scm.ssh.EditMySshKeysWindow.superclass.initComponent.apply(this, arguments);
  },

  onLoad: function(el) {
    var tid = setTimeout(function() {
      el.mask(this.loadingText);
    }, 100);
    Ext.Ajax.request({
      url: this.url,
      method: 'GET',
      scope: this,
      disableCache: true,
      success: function(response) {
        var sshKeys = response.responseText;
        var textArea = Ext.getCmp("editMySshKeysTextArea");
        textArea.setValue(sshKeys);
        clearTimeout(tid);
        el.unmask();
      },
      failure: function(result) {
        clearTimeout(tid);
        el.unmask();
        main.handleRestFailure(result, this.errorTitleText, this.errorMessgeText);
        this.close();
      }
    });
  },

  saveSshKeys: function() {
    var textArea = Ext.getCmp("editMySshKeysTextArea");
    var sshKeys = textArea.getValue();
    var tid = setTimeout(function() {
      this.el.mask(this.loadingText);
    }, 100);
    Ext.Ajax.request({
      url: this.url,
      method: 'PUT',
      params: sshKeys,
      scope: this,
      success: function(response) {
        clearTimeout(tid);
        this.el.unmask();
        this.close();
      },
      failure: function(result) {
        clearTimeout(tid);
        this.el.unmask();
        main.handleRestFailure(result, this.errorTitleText, this.errorMessgeText);
        this.close();
      }
    });
  },

  cancel: function() {
    this.close();
  }

});

// Configuration form for SSH server
Aquenos.scm.ssh.ServerConfigForm = Ext
  .extend(
    Sonia.config.ConfigForm,
    {

      titleText: 'SSH Server Settings',
      listenAddressText: 'Server address',
      listenAddressHelpText: 'The IP address or hostname the SSH server shall listen on. The server will listen on all available interfaces if this option is left empty. The server needs to be restarted for changes in this option to take effect.',
      listenPortText: 'Server port',
      listenPortHelpText: 'The TCP port number the SSH Server shall listen on. The server needs to be restarted for changes in this option to take effect.',
      rsaHostKeyText: 'RSA host key',
      rsaHostKeyHelpText: 'The RSA host key for the SSH server in PEM format. If this field is left empty, a new key will be generated.',
      dsaHostKeyText: 'DSA host key',
      dsaHostKeyHelpText: 'The DSA host key for the SSH server in PEM format. If this field is left empty, a new key will be generated.',
      loadingText: 'Loading data...',
      submitText: 'Transferring data...',
      errorTitleText: 'Communication Error',
      errorMessageText: 'The communication with the server failed.',

      initComponent: function() {
        var config = {
          xtype: 'configForm',
          title: this.titleText,
          items: [ {
            xtype: 'textfield',
            fieldLabel: this.listenAddressText,
            name: 'listenAddress',
            helpText: this.listenAddressHelpText
          }, {
            xtype: 'numberfield',
            fieldLabel: this.listenPortText,
            name: 'listenPort',
            helpText: this.listenPortHelpText,
            allowBlank: false,
            allowDecimals: false,
            allowNegative: false,
            minValue: 1,
            maxValue: 65535
          }, {
            xtype: 'textarea',
            fieldLabel: this.rsaHostKeyText,
            name: 'rsaHostKey',
            helpText: this.rsaHostKeyHelpText,
            autoScroll: true,
            height: 150,
            wordWrap: false
          }, {
            xtype: 'textarea',
            fieldLabel: this.dsaHostKeyText,
            name: 'dsaHostKey',
            helpText: this.dsaHostKeyHelpText,
            autoScroll: true,
            height: 150,
            wordWrap: false
          } ],

          onSubmit: function(values) {
            this.el.mask(this.submitText);
            Ext.Ajax.request({
              url: restUrl + "scm-ssh-plugin/server-config.json",
              method: 'PUT',
              jsonData: values,
              scope: this,
              disableCaching: true,
              success: function(response) {
                this.el.unmask();
                this.loadFormValues(this.el);
              },
              failure: function(result) {
                this.el.unmask();
                main.handleRestFailure(result, this.errorTitleText, this.errorMsgText);
              }
            });
          },

          onLoad: function(el) {
            this.loadFormValues(el);
          }
        };

        Ext.apply(this, Ext.apply(this.initialConfig, config));
        Aquenos.scm.ssh.ServerConfigForm.superclass.initComponent.apply(this, arguments);
      },

      loadFormValues: function(el) {
        var tid = setTimeout(function() {
          el.mask(this.loadingText);
        }, 100);
        Ext.Ajax.request({
          url: restUrl + "scm-ssh-plugin/server-config.json",
          method: 'GET',
          scope: this,
          disableCaching: true,
          success: function(response) {
            var obj = Ext.decode(response.responseText);
            this.load(obj);
            clearTimeout(tid);
            el.unmask();
          },
          failure: function(result) {
            clearTimeout(tid);
            this.el.unmask();
            main.handleRestFailure(result, this.errorTitleText, this.errorMsgText);
          }
        });
      }

    });

Ext.reg('sshServerConfigForm', Aquenos.scm.ssh.ServerConfigForm);

registerGeneralConfigPanel({
  xtype: 'sshServerConfigForm'
});

loginCallbacks.push(function() {
  var parentPanel = Ext.getCmp('navigationPanel');
  // The security section does not have an id, thus we search for it based on
  // its title.
  var securitySection = parentPanel.items.find(function(item) {
    if (item.title == Sonia.scm.Main.prototype.sectionSecurityText) {
      return true;
    } else {
      return false;
    }
  });
  if (securitySection != null) {
    // Insert the link right after the change password link
    var insertAt = 0;
    if (securitySection.links.length >= 1) {
      insertAt = 1;
    }
    var link = {
      label: Aquenos.scm.ssh.EditMySshKeysWindow.prototype.linkText,
      fn: function() {
        new Aquenos.scm.ssh.EditMySshKeysWindow().show();
      }
    };
    // The link must have a unique id in order to work correctly.
    Ext.id(link);
    securitySection.insertLink(insertAt, link);
    securitySection.doLayout();
  }
});

if (i18n != null && i18n.country == "de") {
  Ext
    .override(
      Sonia.user.FormPanel,
      {
        sshKeysText: 'Öffentliche SSH-Schlüssel',
        sshKeysHelpText: 'Öffentliche SSH-Schlüssel des Benutzers im OpenSSH-Format (ein Schlüssel pro Zeile)',
      });
  Ext
    .override(
      Aquenos.scm.ssh.EditMySshKeysWindow,
      {
        linkText: 'Meine SSH-Schlüssel bearbeiten',
        titleText: 'Meine SSH-Schlüssel bearbeiten',
        sshKeysText: 'Öffentliche SSH-Schlüssel',
        sshKeysHelpText: 'Öffentliche SSH-Schlüssel des Benutzers im OpenSSH-Format (ein Schlüssel pro Zeile)',
        okText: 'OK',
        cancelText: 'Abbrechen',
        errorTitleText: 'Verbindungsfehler',
        errorMessageText: 'Die Kommunikation mit dem Server ist fehlgeschlagen.',
        loadingText: 'Lade...'
      });
  Ext
    .override(
      Aquenos.scm.ssh.ServerConfigForm,
      {
        titleText: 'SSH-Server-Einstellungen',
        listenAddressText: 'Server-Adresse',
        listenAddressHelpText: 'Die IP-Adresse oder der Rechnername auf dem der SSH-Server Verbindungen annehmen soll. Wenn dieses Feld leer gelassen wird, nimmt der Server Verbindungen auf allen verfügbaren Schnittstellen entgegen. Der Server muss neugestartet werden, damit Änderungen an dieser Einstellung wirksam werden.',
        listenPortText: 'Server-Port',
        listenPortHelpText: 'Die TCP-Port-Nummer auf welcher der SSH-Server Verbindungen annehmen soll. Der Server muss neugestartet werden, damit Änderungen an dieser Einstellung wirksam werden.',
        rsaHostKeyText: 'RSA-Host-Schlüssel',
        rsaHostKeyHelpText: 'Der RSA-Host-Schlüssel für den SSH-Server im PEM-Format. Wenn dieses Feld leer gelassen wird, wird ein neuer Schlüssel generiert.',
        dsaHostKeyText: 'DSA-Host-Schlüssel',
        dsaHostKeyHelpText: 'Der DSA-Host-Schlüssel für den SSH-Server im PEM-Format. Wenn dieses Feld leer gelassen wird, wird ein neuer Schlüssel generiert.',
        loadingText: 'Lade Daten...',
        submitText: 'Übertrage Daten...',
        errorTitleText: 'Verbindungsfehler',
        errorMessageText: 'Die Kommunikation mit dem Server ist fehlgeschlagen.',
      });
}
