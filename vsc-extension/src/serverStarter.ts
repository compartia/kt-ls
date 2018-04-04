
import { workspace } from 'vscode';

import * as path from 'path';
import * as net from 'net';
import * as glob from 'glob';
import * as PortFinder from "portfinder";
import * as ChildProcess from "child_process";

import { StreamInfo, Executable, ExecutableOptions } from 'vscode-languageclient';
import { RequirementsData } from './requirements';

declare var v8debug:boolean;
const DEBUG = (typeof v8debug === 'object') || startedInDebugMode();

export function prepareExecutable(requirements: RequirementsData): Executable {
    let executable: Executable = Object.create(null);
    let options: ExecutableOptions = Object.create(null);
    options.env = process.env;
    options.stdio = 'pipe';
    executable.options = options;
    executable.command = path.resolve(requirements.java_home + '/bin/java');
    executable.args = prepareParams(requirements);

    console.log(executable);
    return executable;
}


export function createServer(requirements: RequirementsData): Promise<StreamInfo> {

    const javaExecutablePath = path.resolve(requirements.java_home + '/bin/java');

    return new Promise((resolve, reject) => {
        PortFinder.getPort({ port: 55282 }, (err, port) => {
            let fatJar = getJarName();

            let args  = [
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
                    reader:  <NodeJS.ReadableStream>c,
                    writer:  <NodeJS.WritableStream>c
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

                    // console.log(args);
                    // console.log(process);

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


function getJarName() {
    console.log("__dirname=" + __dirname);
    let server_home: string = path.resolve(__dirname, '../../');
    console.log("server_home=" + server_home);

    let launchersFound: Array<string> = glob.sync('**/kt-ls*.jar', { cwd: server_home });
    if (launchersFound.length) {
        const jarname = path.resolve(server_home, launchersFound[0]);
        console.log("jarname=" + jarname);
        return jarname;
    } else {
        return null;
    }


}


function prepareParams(requirements: RequirementsData): string[] | undefined {
    let params: string[] = [];
    if (DEBUG) {
        params.push('-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=1044,quiet=y');
        // suspend=y is the default. Use this form if you need to debug the server startup code:
        //  params.push('-agentlib:jdwp=transport=dt_socket,server=y,address=1044');
    }
    if (requirements.java_version > 8) {
        params.push('--add-modules=ALL-SYSTEM');
        params.push('--add-opens');
        params.push('java.base/java.util=ALL-UNNAMED');
        params.push('--add-opens');
        params.push('java.base/java.lang=ALL-UNNAMED');
    }

    // if (DEBUG) {
    //     params.push('-Dlog.protocol=true');
    //     params.push('-Dlog.level=ALL');
    // }


    params.push('-Dlog.protocol=true');
    params.push('-Dlog.level=ALL');


    params.push('-Dktls.port=8888');//XXX: find free


    const jarname = getJarName();
    if (jarname) {
        params.push('-jar');
        params.push(jarname);
    } else {
        return undefined;
    }

    console.log(params);
    return params;
}


function startedInDebugMode(): boolean {
    let args = (process as any).execArgv;
    if (args) {
        return args.some((arg: string) => /^--debug=?/.test(arg) || /^--debug-brk=?/.test(arg) || /^--inspect-brk=?/.test(arg));
    }
    return false;
}

