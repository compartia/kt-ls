
import { workspace, ExtensionContext } from 'vscode';


import * as path from 'path';
import * as net from 'net';
import * as glob from 'glob';
import * as PortFinder from "portfinder";
import * as ChildProcess from "child_process";

import { StreamInfo, Executable, ExecutableOptions } from 'vscode-languageclient';
import { RequirementsData } from './requirements';

declare var v8debug: boolean;
const DEBUG = (typeof v8debug === 'object') || startedInDebugMode();




export function createServer(requirements: RequirementsData, ctx: ExtensionContext): Promise<StreamInfo> {

    const javaExecutablePath = path.resolve(requirements.java_home + '/bin/java');

    return new Promise((resolve, reject) => {
        PortFinder.getPort({ port: 55282 }, (err, port) => {
            let fatJar = getJarName(ctx);

            let args = [
                '-Dktls.slave=true',
                '-Dclientport=' + port,
                '-jar', fatJar
                // ,
                // '-Xverify:none' // helps VisualVM avoid 'error 62' 
            ];

            console.log('..about to call net.createServer with port ' + port);
            const server = net.createServer((c) => {
                // 'connection' listener
                console.log('client connected');

                c.on('end', () => {
                    console.log('client disconnected');
                });

                resolve({
                    reader: <NodeJS.ReadableStream>c,
                    writer: <NodeJS.WritableStream>c
                });

            });

            server.on('error', (err) => {
                reject();
                throw err;
            });



            server
                .listen(port, () => {
                    // Start the child java process
                    let options = { cwd: workspace.rootPath };
                    let process = ChildProcess.spawn(javaExecutablePath, <string[]>args, options);

                    console.log(args);
                    console.log(process);

                    process.on("error", e => console.log("KT LS error:", e));
                    process.on("exit", (code, signal) => console.log("KT LS done", code, signal));


                    // process.stdout.on('data', (d) => {
                    //     console.log(d.toString())
                    // })

                });
        });
    });
}


export function connectToRunningServer(port: any): Thenable<StreamInfo> {

    let connectionInfo = {
        port: parseInt(port)
    };

    console.log('Connecting to KT LS server on port ' + connectionInfo.port);
    let socket = net.connect(connectionInfo, () => {
        console.log('Connected to KT LS server');
    });

    let result: StreamInfo = {
        writer: <NodeJS.WritableStream>socket,
        reader: <NodeJS.ReadableStream>socket
    };

    socket.on('end', () => {
        console.log('disconnected from server');
    });


    return Promise.resolve(<StreamInfo>result);
}



function getJarName(context: ExtensionContext) {
    console.log("__dirname=" + __dirname);
    let jarname = path.resolve(context.extensionPath, "out", "kt-ls.jar");
    return jarname;
}




function startedInDebugMode(): boolean {
    let args = (process as any).execArgv;
    if (args) {
        return args.some((arg: string) => /^--debug=?/.test(arg) || /^--debug-brk=?/.test(arg) || /^--inspect-brk=?/.test(arg));
    }
    return false;
}

