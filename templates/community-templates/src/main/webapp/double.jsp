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
        <utility:applicationResources/>
    </template:templateHead>
    <template:templateBody>
        <div id="bodywrapper">
            <div id="container"><!--start container-->
                <!-- Head page -->
                <template:include page="common/header.jsp"/>
            </div>
            <!--stop container-->
            <div id="container2"><!--start container2-->
                <div id="container3"><!--start container3-->
                    <div id="wrapper"><!--start wrapper-->
                        <div id="content"><!--start content-->
                            <div class="spaceContent"><!--start spaceContent -->
                                <template:include page="common/breadcrumb.jsp"/>
                                <!--stop box -->
                                <div class="box"><!--start box -->
                                    <h2><c:out value="${requestScope.currentPage.highLightDiffTitle}"/></h2>
                                    <template:include page="common/maincontent/maincontentDisplay.jsp">
                                        <template:param name="id" value="columnB"/>
                                    </template:include>
                                </div>
                                <template:include page="common/box/box.jsp">
                                    <template:param name="name" value="columnB_box"/>
                                </template:include>
                            </div>
                            <!--stop columnspace -->
                            <div class="clear"></div>
                        </div>
                        <!--stop space content-->
                    </div>
                    <!--stopContent-->
                    <!--stop wrapper-->
                    <div id="rightInset"><!--start rightInset-->
                        <div class="space"><!--start space leftInset -->
                            <div class="box"><!--start box -->
                                <template:include page="common/maincontent/maincontentDisplay.jsp">
                                    <template:param name="id" value="columnC"/>
                                </template:include>
                            </div>
                            <template:include page="common/box/box.jsp">
                                <template:param name="name" value="columnC_box"/>
                            </template:include>
                        </div>
                    </div>
                    <!--stop space rightInset-->
                <div id="leftInset"><!--start leftInset-->
                    <div class="space"><!--start space leftInset -->
                        <!-- left menu -->
                        <template:include page="common/leftmenu.jsp"/>
                        <!-- Search area -->
                        <template:include page="common/searchArea.jsp"/>
                    </div>
                    <!--stop space leftInset-->
                </div>
                <!--stop leftInset-->
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




