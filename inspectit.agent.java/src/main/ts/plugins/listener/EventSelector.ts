class EventSelector {

    public readonly events: string[];
    public readonly markAlwaysAsRelevant: boolean;
    public readonly considerBubbling: boolean;

    private readonly storagePrefix: string;
    private readonly selector: string;
    private readonly attributesToExtract: string[];

    public constructor(config: [string, string, string, boolean, boolean, string]) {
        this.events = config[0].split(",");
        this.selector = config[1];
        this.attributesToExtract = config[2].split(",");
        this.markAlwaysAsRelevant = config[3];
        this.considerBubbling = config[4];
        this.storagePrefix = config[5];
    }

    public matchesElement(elem: Element): Element | null {

        if (Util.elementMatchesSelector(elem, this.selector)) {
            return elem;
        } else if (this.considerBubbling) {
            let current: Node | null = elem.parentElement;
            while (current != null) {
                if (Util.isDomElement(current) && Util.elementMatchesSelector(current as Element, this.selector)) {
                    return current as Element;
                }
                current = current.parentNode;
            }
        }
        return null;
    }

    public extractAttributes(elem: Element, storage: IDictionary<string>) {
        for (const attributeName of this.attributesToExtract) {
            if (!(attributeName in storage)) {
                const storageName = this.storagePrefix ? this.storagePrefix + "." + attributeName : attributeName;
                if (attributeName === "$label") {
                    const label = this.getLabelText(elem);
                    if (label) {
                        storage[storageName] = label;
                    }
                } else {
                    if (elem.hasAttribute(attributeName)) {
                        let htmlAttr = elem.getAttribute(attributeName);
                        if (htmlAttr == null) {
                            htmlAttr = "";
                        }
                        storage[storageName] = htmlAttr.toString();
                    } else if ((elem as any)[attributeName] !== undefined && (elem as any)[attributeName] !== "") {
                        storage[storageName] = (elem as any)[attributeName].toString();
                    }
                }
            }
        }
    }

    private getLabelText(elem: any) {
        if ((typeof elem.parentElement) === "object" && (typeof elem.parentElement.getElementsByTagName) === "function") {
            const parent = (elem as Node).parentElement;
            if (parent !== null) {
                const labels = parent.getElementsByTagName("LABEL");
                for (let i = 0; i < labels.length; i++) {
                    const label = labels.item(i);
                    if (label.getAttribute("for") === elem.id) {
                        return (label as any).innerText;
                    }
                }
            }
        }
        return null;
    }

}