<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="jcr" uri="http://www.jahia.org/tags/jcr" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="functions" uri="http://www.jahia.org/tags/functions" %>

<jcr:nodeProperty node="${currentNode}" name="j:node" var="reference"/>
<jcr:nodeProperty node="${currentNode}" name="j:target" var="target"/>
<jcr:nodeProperty node="${currentNode}" name="j:linknode" var="linkreference"/>
<jcr:nodeProperty node="${currentNode}" name="j:alternateText" var="title"/>
<jcr:nodeProperty node="${currentNode}" name="j:url" var="externalUrl"/>

<c:set var="node" value="${reference.node}"/>
<c:if test="${not empty node}">
    <c:url var="imageUrl" value="${node.url}" context="/"/>
    <c:set var="height" value=""/>
    <c:set var="width" value=""/>
    <c:if test="${not empty node.properties['j:height']}">
        <c:set var="height">height="${node.properties['j:height'].string}"</c:set>
    </c:if>
    <c:if test="${not empty node.properties['j:width']}">
        <c:set var="width">width="${node.properties['j:width'].string}"</c:set>
    </c:if>
    <c:if test="${not empty target.string}"><c:set var="target"> target="${target.string}"</c:set></c:if>
    <c:set var="linknode" value="${linkreference.node}"/>
    <c:if test="${not empty linknode}">
        <c:url var="linkUrl" value="${url.base}${linknode.path}.html"/>
    </c:if>
    <c:if test="${empty linkUrl and not empty externalUrl}">
        <c:if test="${!functions:matches('^[A-Za-z]*:.*', externalUrl.string)}"><c:set var="protocol">http://</c:set></c:if>
        <c:url var="linkUrl" value="${protocol}${externalUrl.string}"/>
    </c:if>
    <c:if test="${!empty linkUrl}">
        <a href="${linkUrl}" ${target}>
    </c:if>

    <img src="${imageUrl}" alt="${fn:escapeXml(not empty title.string ? title.string : currentNode.name)}" <c:out value="${height} ${width}" escapeXml="false"/> />
    <c:if test="${!empty linkUrl}">
        </a>
    </c:if>
</c:if>
<c:if test="${empty node and renderContext.editMode}">
    <fmt:message key="label.empty"/>
</c:if>
