<%--

    Jahia Enterprise Edition v6

    Copyright (C) 2002-2009 Jahia Solutions Group. All rights reserved.

    Jahia delivers the first Open Source Web Content Integration Software by combining Enterprise Web Content Management
    with Document Management and Portal features.

    The Jahia Enterprise Edition is delivered ON AN "AS IS" BASIS, WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESSED OR
    IMPLIED.

    Jahia Enterprise Edition must be used in accordance with the terms contained in a separate license agreement between
    you and Jahia (Jahia Sustainable Enterprise License - JSEL).

    If you are unsure which license is appropriate for your use, please contact the sales department at sales@jahia.com.

--%>
<%@ page language="java" contentType="text/html;charset=UTF-8" %>
<%@ include file="common/declarations.jspf" %>
<template:template>
    <template:templateHead>
        <!-- All headers and declarations global meta and css-->
        <%@ include file="common/head_externals.jspf" %>
    </template:templateHead>
    <template:templateBody gwtScript="mediagallery">
        <div id="bodywrapper">
            <div id="container"><!--start container-->
                <!-- Head page -->
                <template:include page="common/header.jsp"/>
            </div>
            <!--stop container-->
            <div id="container2"><!--start container2-->
                <div id="container3"><!--start container3-->
                    <div id="wrapper"><!--start wrapper-->
                        <div id="content4"><!--start content-->
                            <div class="spaceContent"><!--start spaceContent -->
                                <template:include page="common/breadcrumb.jsp"/>
                                <div class="box">
                                    <h2><c:out value="${requestScope.currentPage.highLightDiffTitle}"/></h2>

                                    <ui:mediaGallery name="myGallery"
                                                     inputName="selectedPath"
                                                     cssClassName="mediaGallery"/>
                                </div>
                            </div>
                            <!--stop space content-->
                        </div>
                        <!--stopContent-->
                    </div>
                    <!--stop wrapper-->
                    <div class="clear"></div>
                </div>
                <!--stop container2-->
                <!-- footer -->
                <template:include page="common/footer.jsp"/>
                <div class="clear"></div>
            </div>
            <!--stop container3-->
        </div>
    </template:templateBody>
</template:template>