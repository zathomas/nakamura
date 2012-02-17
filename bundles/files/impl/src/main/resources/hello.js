var requiresMigration = function(structure0String, originalstructureString, returnValue){
    console.log('Starting out...');
    console.log(typeof structure0String);
    var structure0;
    if (typeof structure0String === "string"){
        console.log('Going to parse JSON for structure0String');
        var structure0 = $.parseJSON(structure0String);
    } else {
        structure0 = structure0String;
    }
    var originalstructure = $.parseJSON(originalstructureString);
    $.each(structure0, function(key, item){
        console.log('Examining key ' + key);
        if (key.substring(0, 1) !== "_"){
            var ref = item._ref;
            if (originalstructure[ref]){
                if (!originalstructure[ref].rows) {
                    returnValue = true;
                }
            }
            returnValue = requiresMigration(item, originalstructureString, returnValue);
        }
    });
    return returnValue;
}

/**
 * Run through the structure0 page structure object and update all of the
 * pages that haven't been migrated yet
 * @param {Object} structure0            Sakai Doc's structure0 object. Contains
 *                                       the page structure and references
 * @param {Object} originalstructure     The full Sakai Doc object. Contains the
 *                                       full structure0 object and page objects
 * @param {Object} json                  Migrated object that will be returned
 */
var processStructure0 = function(structure0, originalstructure, json){

    /**
     * Finish a row and add the row to the page
     * @param {Object} currentRow            Object that represents the current row, and its columns and cells
     * @param {Object} currentPage           Object representing the migrated Sakai Doc page so far
     * @param {Object} columnsForNextRow     How many columns the next row should have
     * @param {Object} currentHTMLBlock      The collected text so far
     */
    var addRowToPage = function(currentRow, currentPage, columnsForNextRow, currentHTMLBlock){
        if (currentHTMLBlock && sakai_util.determineEmptyContent(currentHTMLBlock.html())){
            currentPage = generateNewCell(false, "htmlblock", currentPage, currentRow, false, {
                "htmlblock": {
                    "content": currentHTMLBlock.html()
                }
            });
        }
        currentHTMLBlock = $("<div />");
        var rowHasContent = false;
        for (var c = 0; c < currentRow.columns.length; c++){
            if (currentRow.columns[c].elements.length){
                rowHasContent = true;
            }
        }
        if (rowHasContent){
            currentPage.rows.push(currentRow);
        }
        // Generate the first empty row
        return generateEmptyRow(columnsForNextRow || 1);
    }

    /**
     * Generate a new empty row to addd to the page
     * @param {Object} columnCount    Number of columns in this row
     */
    var generateEmptyRow = function(columnCount){
        var row = {
            "id": sakai_util.generateWidgetId(),
            "columns": []
        };
        for (var c = 0; c < columnCount; c++){
            row.columns.push({
                "width": 1 / columnCount,
                "elements": []
            })
        };
        return row;
    };

    /**
     * Generate a new widget cell in the last row
     * @param {Object} id            Widget id
     * @param {Object} type          Widget type
     * @param {Object} currentPage   Object representing the migrated Sakai Doc page so far
     * @param {Object} column        The index of the column in which to insert the widget
     * @param {Object} data          The widget's data
     */
    var generateNewCell= function(id, type, currentPage, currentRow, column, data){
        if (type !== "tooltip" && type !== "joinrequestbuttons") {
            id = id || sakai_util.generateWidgetId();
            currentRow.columns[column || 0].elements.push({
                "id": id,
                "type": type
            });
            if (data) {
                currentPage[id] = data;
            }
        }
        return currentPage;
    };

    /**
     * Make sure that the page has at least one row available, otherwise the user
     * won't be able to edit the page
     * @param {Object} currentPage    Object representing the migrated Sakai Doc page so far
     */
    var ensureRowPresent = function(currentPage){
        if (currentPage.rows.length === 0){
            currentPage.rows.push(generateEmptyRow(1));
        }
    };

    if (typeof structure0 === "string") {
        structure0 = $.parseJSON(structure0);
    }
    $.each(structure0, function(key, item){
        // Keys with an underscore are system properties. Only keys that
        // don't start with an _ indicate a page
        console.log('Examining key ' + key + ' of type ' + typeof key);
        if (key.substring(0, 1) !== "_"){
            var ref = item._ref;
            if (typeof originalstructure === "string") {
                originalstructure = $.parseJSON(originalstructure);
            }
            if (originalstructure[ref]){
                // The page has been migrated if there is a rows property
                if (originalstructure[ref].rows) {
                    json[ref] = originalstructure[ref];
                    // The page doesn't have a rows property. Needs to be migrated
                } else {
                    // Original page content --> Convert into a jQuery object
                    var page = $(originalstructure[ref].page);

                    // Array that will hold all the rows for this page
                    var currentPage = {};
                    currentPage.rows = [];
                    var currentHTMLBlock = $("<div />");
                    // Generate the first empty row
                    var currentRow = generateEmptyRow(1);

                    // Run through all of the top-level elements in the page
                    $.each(page, function(index, topLevelElement){
                        var $topLevelElement = $(topLevelElement);

                        // Check whether the top level element is a widget
                        if ($topLevelElement.hasClass("widget_inline")) {

                            // If we have collected any text for our htmlblock widget, we add it to the page
                            if (sakai_util.determineEmptyContent(currentHTMLBlock.html())) {
                                currentPage = generateNewCell(false, "htmlblock", currentPage, currentRow, false, {
                                    "htmlblock": {
                                        "content": currentHTMLBlock.html()
                                    }
                                });
                            }
                            currentHTMLBlock = $("<div />");

                            // Add the widget to the page
                            var widgetId = $topLevelElement.attr("id").split("_");
                            var widgetType = widgetId[1];
                            widgetId = widgetId.length > 2 ? widgetId[2] : sakai_util.generateWidgetId();
                            // Filter out widgets that should not be re-included as they are already in topnavigation
                            currentPage = generateNewCell(widgetId, widgetType, currentPage, currentRow, false, originalstructure[widgetId]);

                            // Check whether any of the child elements are widgets
                        } else if ($(".widget_inline", $topLevelElement).length > 0) {

                            // If we have collected any text for our htmlblock widget, we add it to the page
                            if (sakai_util.determineEmptyContent(currentHTMLBlock.html())) {
                                currentPage = generateNewCell(false, "htmlblock", currentPage, currentRow, false, {
                                    "htmlblock": {
                                        "content": currentHTMLBlock.html()
                                    }
                                });
                            }
                            currentHTMLBlock = $("<div />");

                            // Check how many columns we'll need
                            var columns = 1;
                            // If there are any left floating widgets, we'll need a left column
                            var left = $(".widget_inline.block_image_left", $topLevelElement).length ? 1 : 0;
                            columns += left ? 1 : 0;
                            // If there are any right floating widgets, we'll need a right column
                            var right = $(".widget_inline.block_image_right", $topLevelElement).length ? 1 : 0;
                            columns += right ? 1 : 0;

                            // Create a new row with multiple columns
                            if (columns > 1){
                                currentRow = addRowToPage(currentRow, currentPage, columns, currentHTMLBlock);
                            }

                            $.each($(".widget_inline", $topLevelElement), function(index2, widgetElement){
                                $widgetElement = $(widgetElement);

                                // Add the widget to the page
                                var widgetId = $widgetElement.attr("id").split("_");
                                var widgetType = widgetId[1];
                                widgetId = widgetId.length > 2 ? widgetId[2] : sakai_util.generateWidgetId();

                                // If the widget was floating left, add it to the left column
                                if ($widgetElement.hasClass("block_image_left")) {
                                    currentPage = generateNewCell(widgetId, widgetType, currentPage, currentRow, 0, originalstructure[widgetId]);
                                    // If the widget was floating left, add it to the right column
                                } else if ($widgetElement.hasClass("block_image_right")) {
                                    currentPage = generateNewCell(widgetId, widgetType, currentPage, currentRow, (left ? 2 : 1), originalstructure[widgetId]);
                                    // If the widget was not floating at all, add it to the central (text) column
                                } else {
                                    currentPage = generateNewCell(widgetId, widgetType, currentPage, currentRow, (left ? 1 : 0), originalstructure[widgetId]);
                                }
                                $($widgetElement, $topLevelElement).remove();
                            });

                            currentPage = generateNewCell(false, "htmlblock", currentPage, currentRow, (left ? 1 : 0), {
                                "htmlblock": {
                                    "content": $topLevelElement.html()
                                }
                            });

                            // Create a new row for the next top level element
                            if (columns > 1){
                                currentRow = addRowToPage(currentRow, currentPage, 1, currentHTMLBlock);
                            }

                            // There is only text in the current top element. Just append it to the collected text
                        } else {
                            currentHTMLBlock.append($topLevelElement);

                        }
                    });

                    // Add the remaining collected text as the last element of the page
                    addRowToPage(currentRow, currentPage, 1, currentHTMLBlock);
                    ensureRowPresent(currentPage);

                    // Add the converted page to the migrated Sakai Doc
                    json[ref] = currentPage;

                }
            }

            // Continue recursively to do the same for all the subpages
            processStructure0(item, originalstructure, json);
        }
    });
    return json;
}