/*
 * Copyright © 2015 Stefan Niederhauser (nidin@gmx.ch)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package guru.nidi.graphviz.model;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.annotation.Nullable;
import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.*;
import java.io.*;
import java.util.function.Consumer;

public class SvgElementFinder {
    private static final DocumentBuilderFactory FACTORY = builderFactory();
    private static final TransformerFactory TRANSFORMER_FACTORY = transformerFactory();
    private static final VariableResolver RESOLVER = new VariableResolver();
    private static final XPath X_PATH = xPath(RESOLVER);
    private static final XPathExpression EXPR_G = pathExpression(X_PATH, "//g");
    private static final XPathExpression EXPR_TITLE = pathExpression(X_PATH, "//title[text()=$var]");
    private static final XPathExpression EXPR_TITLE_OR = pathExpression(X_PATH, "//title[text()=$var or text()=$alt]");
    private final boolean hasHeader;
    private final Document doc;

    public static String use(String svg, Consumer<SvgElementFinder> actions) {
        final SvgElementFinder finder = new SvgElementFinder(svg);
        actions.accept(finder);
        return finder.getSvg();
    }

    public SvgElementFinder(String svg) {
        try {
            doc = builder().parse(new InputSource(new StringReader(svg)));
            hasHeader = svg.startsWith("<?xml");
        } catch (SAXException | IOException e) {
            throw new AssertionError("Could not read SVG", e);
        }
    }

    public String getSvg() {
        final StringWriter sw = new StringWriter();
        try {
            TRANSFORMER_FACTORY.newTransformer().transform(new DOMSource(doc), new StreamResult(sw));
            final String out = sw.toString().replace("xmlns=\"\"", ""); //rasterizer don't like empty xmlns !?
            return hasHeader ? out : out.substring(out.indexOf("?>") + 2);
        } catch (TransformerException e) {
            throw new AssertionError("Could not generate string from DOM", e);
        }
    }

    public Element findGraph() {
        return (Element) nodeExpr(EXPR_G, "");
    }

    @Nullable
    public Element findNode(guru.nidi.graphviz.model.Node node) {
        return findNode(node.name().toString());
    }

    @Nullable
    public Element findNode(String name) {
        final org.w3c.dom.Node title = nodeExpr(EXPR_TITLE, name);
        return title == null ? null : (Element) title.getParentNode();
    }

    @Nullable
    public Element findLink(Link link) {
        return findLink(link.from().name().toString(), link.to().name().toString());
    }

    @Nullable
    public Element findLink(String from, String to) {
        final org.w3c.dom.Node title = nodeExpr(EXPR_TITLE_OR, from + "--" + to);
        return title == null ? null : (Element) title.getParentNode();
    }

    @Nullable
    public Element findCluster(Graph cluster) {
        return findCluster(cluster.name().toString());
    }

    @Nullable
    public Element findCluster(String name) {
        final org.w3c.dom.Node title = nodeExpr(EXPR_TITLE, "cluster_" + name);
        return title == null ? null : (Element) title.getParentNode();
    }

    @Nullable
    private org.w3c.dom.Node nodeExpr(XPathExpression expr, String var) {
        RESOLVER.set(var);
        try {
            return (org.w3c.dom.Node) expr.evaluate(doc, XPathConstants.NODE);
        } catch (XPathExpressionException e) {
            throw new AssertionError("Could not execute XPath", e);
        }
    }

    private static DocumentBuilderFactory builderFactory() {
        try {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            return factory;
        } catch (ParserConfigurationException e) {
            throw new AssertionError("Could not initialize DOM", e);
        }
    }

    private static TransformerFactory transformerFactory() {
        try {
            final TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            return factory;
        } catch (TransformerConfigurationException e) {
            throw new AssertionError("Could not initialize DOM", e);
        }
    }

    private DocumentBuilder builder() {
        try {
            return FACTORY.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new AssertionError("Could not initialize DOM", e);
        }
    }

    private static XPath xPath(XPathVariableResolver resolver) {
        final XPath xPath = XPathFactory.newInstance().newXPath();
        xPath.setXPathVariableResolver(resolver);
        return xPath;
    }

    private static XPathExpression pathExpression(XPath xPath, String exp) {
        try {
            return xPath.compile(exp);
        } catch (XPathExpressionException e) {
            throw new AssertionError("Invalid XPath expression", e);
        }
    }

    private static class VariableResolver implements XPathVariableResolver {
        private static final ThreadLocal<String> VAR = new ThreadLocal<>();

        public void set(String value) {
            VAR.set(value);
        }

        @Override
        public Object resolveVariable(QName varName) {
            return varName.getLocalPart().equals("var") ? VAR.get() : VAR.get().replace("--", "->");
        }
    }
}
