export type ScheduledTk = {
    cancel: () => boolean
};

export type NudeTk<T> = {
    run: <U>(then: (v: T) => U) => ScheduledTk
};

type PrimitiveTk<T> = {
    flatMap:  <U>(f: ((t: T) => NudeTk<U>)) => Tk<U>,
    andThen:  <U>(t: NudeTk<U>) => Tk<U>,
    map: <U>(f: (t: T) => U) => Tk<U>,
    eval: () => ScheduledTk
};

export type Tk<T> = PrimitiveTk<T> & NudeTk<T>;

const doNothing = <_T, _U>() => () => {
    return undefined as unknown as _U;
};

const mapTk = <T, U>(t: NudeTk<T>, f: ((t: T) => U)): NudeTk<U> => ({
    run: <V>(then: (v: U) => V = doNothing<U, V>()): ScheduledTk =>
        t.run(v => then(f(v)))
});

const flatMapTk = <T, U>(t: NudeTk<T>, f: ((t: T) => NudeTk<U>)): NudeTk<U> => ({
    run: <V>(then: (v: U) => V = doNothing<U, V>()): ScheduledTk => {
        let scheduledSnd: ScheduledTk;
        const scheduledFst = t.run((v2: T) => {
            scheduledSnd = f(v2).run(then);
        });
        return {
            cancel: () => {
                const fstCancelRes = scheduledFst.cancel();
                return scheduledSnd ? fstCancelRes || scheduledSnd.cancel() : fstCancelRes;
            }
        };
    }
});

const createTk = <T>(nudeTk: NudeTk<T>) => {
    const tkT: Tk<T> = {
        ...nudeTk,
        map: <V>(f: (v: T) => V) => createTk(mapTk<T, V>(tkT, f)),
        flatMap: <V>(f: (t: T) => NudeTk<V>) => createTk(flatMapTk<T, V>(tkT, f)),
        andThen: <V>(t: NudeTk<V>) => createTk(flatMapTk<T, V>(tkT, () => t)),
        eval: () => nudeTk.run(doNothing)
    }
    return tkT;
}


export namespace Tk {
    export namespace Http {
        export type Opt = {
            url: string,
            body?: any,
            method?: "POST" | "GET" | "PUT" | "DELETE",
            headers?: { [k: string]: string }
        }
        export type Res<T> = {
            headers: Headers,
            data: T,
            status: number
        }
    }

    export namespace Pipe {
        export const toJson = <T>(res: Response, err?: (reason: any) => any): NudeTk<T> => ({
            run: <U>(then: ((v: T) => U)) => {
                res.json().then(then, err)
                return {
                    cancel: () => false
                };
            }
        })
    }

    export const create = createTk;

    export const lambda = <T>(action: () => T): Tk<T> => createTk({
        run: <U>(then: ((v: T) => U) = doNothing<T, U>()) => {
            then(action());
            return {
                cancel: () => false
            };
        }
    });

    export const http = <T>({headers = {}, body, method = "GET", url}: Http.Opt, err?: (e: any) => any): Tk<Response> => createTk({
        run: <U>(then: ((v: Response) => U) = doNothing<T, U>()) => {
            const httpHeaders = new Headers();
            for (const [name, value] of Object.entries(headers))
                httpHeaders.append(name, value);
            fetch(url, { method, body: body ? JSON.stringify(body): undefined, headers: httpHeaders})
                .then(then, err);
            return {
                cancel: () => false
            };
        }
    });

    export const noop = lambda(doNothing<void, void>());

    type allFunction = (<T1, T2>(t1: Tk<T1>, t2: Tk<T2>) => Tk<[T1, T2]>)
        & (<T1, T2, T3>(t1: Tk<T1>, t2: Tk<T2>, t3: Tk<T3>) => Tk<[T1, T2, T3]>)
        & (<T1, T2, T3, T4>(t1: Tk<T1>, t2: Tk<T2>, t3: Tk<T3>, t4: Tk<T4>) => Tk<[T1, T2, T3, T4]>)
        & (<T1, T2, T3, T4, T5>(t1: Tk<T1>, t2: Tk<T2>, t3: Tk<T3>, t4: Tk<T4>, t5: Tk<T5>) => Tk<[T1, T2, T3, T4, T5]>)
        & (<T>(...ts: Tk<T>[]) => Tk<T[]>);

    export const all = (<T>(...tasks: Tk<T>[]): Tk<T[]> => createTk({
        run: <U>(then: ((v: T[]) => U) = doNothing<T, U>()) => {
            let remainingTasks = tasks.length;
            const taskValues: T[] = [];
            const scheduledTasks = tasks.map((t, i) =>
                t.run(v => {
                    taskValues[i] = v;
                    if (--remainingTasks == 0) {
                        then(taskValues);
                    }
                })
            );
            return {
                cancel: () =>
                    scheduledTasks
                        .map(t => t.cancel())
                        .reduce((acc, current) => acc || current, false)
            };
        }
    })) as allFunction;
}