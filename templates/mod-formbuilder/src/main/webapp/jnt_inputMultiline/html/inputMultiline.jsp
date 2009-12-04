<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<c:set value="${currentNode.propertiesAsString}" var="props"/>

<p class="field">
    <label for="${currentNode.name}">${props.label}</label>
    <textarea type="text" name="${currentNode.name}" cols="${props.cols}" cols="${props.rows}"/> ${props.defaultValue}
    </textarea>
</p>