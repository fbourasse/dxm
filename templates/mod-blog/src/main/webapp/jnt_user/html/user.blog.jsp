<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="jcr" uri="http://www.jahia.org/tags/jcr" %>
<%@ taglib prefix="utility" uri="http://www.jahia.org/tags/utilityLib" %>
<%@ taglib prefix="template" uri="http://www.jahia.org/tags/templateLib" %>

<jsp:useBean id="now" class="java.util.Date"/>
<c:set var="fields" value="${currentNode.propertiesAsString}"/>
<c:set var="person" value="${fields['j:title']} ${fields['j:firstName']} ${fields['j:lastName']}"/>
<jcr:nodeProperty node="${currentNode}" name="j:birthDate" var="birthDate"/>
<c:if test="${not empty birthDate}">
    <fmt:formatDate value="${birthDate.date.time}" pattern="yyyy" var="birthYear"/>
    <fmt:formatDate value="${now}" pattern="yyyy" var="currentYear"/>
</c:if>
<c:if test="${not empty birthDate}">
    <fmt:formatDate value="${birthDate.date.time}" pattern="dd/MM/yyyy" var="editBirthDate"/>
</c:if>
<fmt:formatDate value="${now}" pattern="dd/MM/yyyy" var="editNowDate"/>

<div class="aboutMeListItem"><!--start aboutMeListItem -->
    <h3><fmt:message key="jnt_blog.aboutMe"/></h3>

    <div class="aboutMePhoto">
        <jcr:nodeProperty var="picture" node="${currentNode}" name="j:picture"/>
        <c:if test="${not empty picture}">
            <img src="${picture.node.thumbnailUrls['avatar_120']}" alt="${fn:escapeXml(person)}"/>
        </c:if>
    </div>
    <div class="aboutMeBody"><!--start aboutMeBody -->
        <h5>${person}</h5>

        <p class="aboutMeAge"><span class="label"><fmt:message
                                    key="jnt_user.profile.age"/> : </span> ${currentYear - birthYear}</p>

        <div class="clear"></div>

    </div>
    <!--stop aboutMeBody -->
    <p class="aboutMeResume">${fields['j:about']}</p>

    <div class="aboutMeAction">
        <a class="aboutMeMore" href="${url.base}${currentNode.path}.html" title="title"><fmt:message
                                    key="jnt_blog.userProfile"/></a>
    </div>
    <div class="clear"></div>
</div>

