'use strict';

import * as net from 'net';

import { Trace } from 'vscode-jsonrpc';
import { workspace, ExtensionContext, window, commands, Uri } from 'vscode';
import { LanguageClient, LanguageClientOptions, StreamInfo, Position as LSPosition, Location as LSLocation } from 'vscode-languageclient';

import { connectToRunningServer, prepareExecutable, createServer } from './serverStarter';
import * as requirements from './requirements';




// this method is called when  extension is activated
// extension is activated the very first time the command is executed
export function activate(context: ExtensionContext) {
    return requirements.resolveRequirements()

        .catch(error => {
            //show error
            window.showErrorMessage(error.message, error.label).then((selection) => {
                if (error.label && error.label === selection && error.openUrl) {
                    commands.executeCommand('vscode.open', error.openUrl);
                }
            });
            // rethrow to disrupt the chain.
            throw error;
        })

        .then(requirements => {



            let serverOptions;

            let port = process.env['KT_LS_SERVER_PORT'];
            if (!port) {
                serverOptions = () => {return createServer(requirements);} ;//prepareExecutable(requirements);
            } else {
                // used during development
                serverOptions = connectToRunningServer.bind(null, port);
            }


            let clientOptions: LanguageClientOptions = {
                documentSelector: ['c'],
                synchronize: {
                    fileEvents: workspace.createFileSystemWatcher('**/*.*')
                }
            };

            // Create the language client and start the client.
            let lc = new LanguageClient('KT Server', serverOptions, clientOptions);


            // enable tracing (.Off, .Messages, Verbose)
            lc.trace = Trace.Verbose;
            let disposable = lc.start();

            // Push the disposable to the context's subscriptions so that the 
            // client can be deactivated on extension deactivation    
            context.subscriptions.push(disposable);



        });


}

// this method is called when your extension is deactivated
export function deactivate() {
}